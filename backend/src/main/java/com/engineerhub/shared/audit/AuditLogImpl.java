package com.engineerhub.shared.audit;

import com.engineerhub.shared.web.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Service
class AuditLogImpl implements AuditLog {

  static final byte[] GENESIS = new byte[32]; // prev_hash of the first row
  private static final long CHAIN_LOCK =
      7_001_001L; // pg advisory lock key: serialises appends to keep the chain linear

  private final JdbcClient jdbc;
  private final Clock clock;
  private final JsonMapper canonical =
      JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();

  AuditLogImpl(JdbcClient jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void record(Authentication actor, UUID actorUserId, AuditEvent e) {
    if (actor == null
        || e == null
        || blank(e.action())
        || blank(e.resourceType())
        || blank(e.resourceId())) {
      throw new IllegalArgumentException("incomplete audit entry");
    }
    // PostgreSQL stores microseconds. A JVM clock with nanosecond precision (Linux) would make the
    // hashed
    // timestamp differ from the stored one and break verification, so truncate before hashing AND
    // inserting.
    Instant at = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    String roles =
        actor.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(a -> a.startsWith("ROLE_"))
            .map(a -> a.substring(5))
            .sorted()
            .collect(Collectors.joining(","));
    String before = json(e.before());
    String after = json(e.after());
    HttpServletRequest req = request();
    String remote = req == null ? null : clip(req.getRemoteAddr(), 64);
    String forwarded = req == null ? null : clip(clean(req.getHeader("X-Forwarded-For")), 200);
    String ua = req == null ? null : clip(clean(req.getHeader("User-Agent")), 300);

    jdbc.sql("select pg_advisory_xact_lock(:k)").param("k", CHAIN_LOCK).query().singleRow();
    byte[] prev =
        jdbc.sql("select hash from admin.audit_logs order by id desc limit 1")
            .query(byte[].class)
            .optional()
            .orElse(GENESIS);
    String correlation = CorrelationId.current();
    byte[] hash =
        hash(
            prev,
            at,
            actorUserId,
            actor.getName(),
            roles,
            e.action(),
            e.resourceType(),
            e.resourceId(),
            before,
            after,
            clip(e.reason(), 500),
            correlation);

    jdbc.sql(
            """
            insert into admin.audit_logs (occurred_at, actor_id, actor_sub, actor_roles, action, resource_type, resource_id,
              details_before, details_after, reason, remote_addr, forwarded_for, user_agent, correlation_id, prev_hash, hash)
            values (:at,:aid,:sub,:roles,:action,:rtype,:rid,:before,:after,:reason,:remote,:fwd,:ua,:corr,:prev,:hash)
            """)
        .param("at", Timestamp.from(at))
        .param("aid", actorUserId)
        .param("sub", actor.getName())
        .param("roles", roles)
        .param("action", e.action())
        .param("rtype", e.resourceType())
        .param("rid", e.resourceId())
        .param("before", before)
        .param("after", after)
        .param("reason", clip(e.reason(), 500))
        .param("remote", remote)
        .param("fwd", forwarded)
        .param("ua", ua)
        .param("corr", correlation)
        .param("prev", prev)
        .param("hash", hash)
        .update();
  }

  /** Canonical, order-independent text of the fields that are protected by the hash chain. */
  static byte[] hash(
      byte[] prev,
      Instant at,
      UUID actorId,
      String sub,
      String roles,
      String action,
      String type,
      String id,
      String before,
      String after,
      String reason,
      String correlation) {
    String canonicalText =
        String.join(
            "\u001f",
            at.toString(),
            String.valueOf(actorId),
            sub,
            roles,
            action,
            type,
            id,
            String.valueOf(before),
            String.valueOf(after),
            String.valueOf(reason),
            String.valueOf(correlation));
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(prev);
      md.update(canonicalText.getBytes(StandardCharsets.UTF_8));
      return md.digest();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private String json(Map<String, Object> m) {
    return m == null ? null : canonical.writeValueAsString(m);
  }

  private static HttpServletRequest request() {
    return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a
        ? a.getRequest()
        : null;
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private static String clip(String v, int max) {
    return v == null ? null : (v.length() > max ? v.substring(0, max) : v);
  }

  /** Header values are attacker-controlled: drop control characters before storing. */
  private static String clean(String v) {
    return v == null ? null : v.replaceAll("\\p{Cntrl}", "");
  }

  static boolean same(byte[] a, byte[] b) {
    return Arrays.equals(a, b);
  }
}
