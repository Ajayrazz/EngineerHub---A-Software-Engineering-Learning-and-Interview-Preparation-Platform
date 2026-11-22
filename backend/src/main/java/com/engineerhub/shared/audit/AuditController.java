package com.engineerhub.shared.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read access to the audit log (permission {@code audit.read}; the permission model lives in the
 * identity module).
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
class AuditController {

  record Entry(
      long id,
      Instant occurredAt,
      String actorSub,
      String actorRoles,
      String action,
      String resourceType,
      String resourceId,
      String before,
      String after,
      String reason,
      String correlationId) {}

  record PageOfEntries(List<Entry> items, int page, int size, boolean hasNext) {}

  private final JdbcClient jdbc;
  private final AuditVerifier verifier;

  AuditController(JdbcClient jdbc, AuditVerifier verifier) {
    this.jdbc = jdbc;
    this.verifier = verifier;
  }

  @GetMapping
  @PreAuthorize("@authz.can('audit.read')")
  PageOfEntries search(
      @RequestParam(required = false) String actor,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String resourceType,
      @RequestParam(required = false) String resourceId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    int pageSize = Math.max(1, Math.min(size, 100));
    int offset = Math.max(0, page) * pageSize;
    StringBuilder where = new StringBuilder(" where true");
    List<Object[]> params = new ArrayList<>();
    add(where, params, "actor_sub = :actor", "actor", actor);
    add(where, params, "action = :action", "action", action);
    add(where, params, "resource_type = :rtype", "rtype", resourceType);
    add(where, params, "resource_id = :rid", "rid", resourceId);
    if (from != null) {
      where.append(" and occurred_at >= :from");
      params.add(new Object[] {"from", java.sql.Timestamp.from(from)});
    }
    if (to != null) {
      where.append(" and occurred_at < :to");
      params.add(new Object[] {"to", java.sql.Timestamp.from(to)});
    }

    var spec =
        jdbc.sql(
            "select id, occurred_at, actor_sub, actor_roles, action, resource_type, resource_id, details_before, details_after, reason, correlation_id from admin.audit_logs"
                + where
                + " order by id desc limit :limit offset :offset");
    for (Object[] p : params) {
      spec = spec.param((String) p[0], p[1]);
    }
    List<Entry> rows =
        spec.param("limit", pageSize + 1)
            .param("offset", offset)
            .query(
                (rs, i) ->
                    new Entry(
                        rs.getLong(1),
                        rs.getTimestamp(2).toInstant(),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5),
                        rs.getString(6),
                        rs.getString(7),
                        rs.getString(8),
                        rs.getString(9),
                        rs.getString(10),
                        rs.getString(11)))
            .list();
    boolean hasNext = rows.size() > pageSize;
    return new PageOfEntries(
        hasNext ? rows.subList(0, pageSize) : rows, Math.max(0, page), pageSize, hasNext);
  }

  /** Recomputes the hash chain end to end. */
  @GetMapping("/verify")
  @PreAuthorize("@authz.can('audit.read')")
  AuditVerifier.Result verify() {
    return verifier.verify();
  }

  private static void add(
      StringBuilder where, List<Object[]> params, String clause, String name, String value) {
    if (value != null && !value.isBlank()) {
      where.append(" and ").append(clause);
      params.add(new Object[] {name, value});
    }
  }
}
