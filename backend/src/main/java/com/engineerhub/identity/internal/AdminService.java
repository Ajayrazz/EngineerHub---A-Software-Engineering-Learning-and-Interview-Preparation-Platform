package com.engineerhub.identity.internal;

import com.engineerhub.identity.Authorizer;
import com.engineerhub.identity.UserDirectory;
import com.engineerhub.identity.UserRef;
import com.engineerhub.identity.internal.AdminDtos.GrantRequest;
import com.engineerhub.identity.internal.AdminDtos.GrantView;
import com.engineerhub.identity.internal.AdminDtos.RoleView;
import com.engineerhub.identity.internal.AdminDtos.UserPage;
import com.engineerhub.identity.internal.AdminDtos.UserView;
import com.engineerhub.shared.audit.AuditEvent;
import com.engineerhub.shared.audit.AuditLog;
import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import com.engineerhub.shared.id.Ids;
import com.engineerhub.shared.messaging.OutboxPublisher;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administrative use cases. Every state change is audited in the same transaction. */
@Service
class AdminService {

  private static final Duration MAX_GRANT_LIFETIME = Duration.ofDays(365);

  private final JdbcClient jdbc;
  private final UserAccountRepository users;
  private final UserDirectory directory;
  private final Authorizer authz;
  private final AuditLog audit;
  private final OutboxPublisher events;
  private final UserStatusCache statusCache;
  private final PermissionCatalog catalog;
  private final Clock clock;
  private final RoleAssignmentService roleAssignment;

  AdminService(
      JdbcClient jdbc,
      UserAccountRepository users,
      UserDirectory directory,
      Authorizer authz,
      AuditLog audit,
      OutboxPublisher events,
      UserStatusCache statusCache,
      PermissionCatalog catalog,
      Clock clock,
      RoleAssignmentService roleAssignment) {
    this.jdbc = jdbc;
    this.users = users;
    this.directory = directory;
    this.authz = authz;
    this.audit = audit;
    this.events = events;
    this.statusCache = statusCache;
    this.catalog = catalog;
    this.clock = clock;
    this.roleAssignment = roleAssignment;
  }

  // ------------------------------------------------------------------ users

  @Transactional(readOnly = true)
  UserPage list(String q, String status, int page, int size) {
    int pageSize = Math.max(1, Math.min(size, 100));
    int offset = Math.max(0, page) * pageSize;
    StringBuilder where = new StringBuilder(" where true");
    Map<String, Object> params = new LinkedHashMap<>();
    if (q != null && !q.isBlank()) {
      // prefix search; LIKE wildcards in the input are escaped so "%" cannot be used to dump
      // everything
      String like =
          q.strip()
                  .toLowerCase(java.util.Locale.ROOT)
                  .replace("\\", "\\\\")
                  .replace("%", "\\%")
                  .replace("_", "\\_")
              + "%";
      where.append(
          " and (lower(username) like :like escape '\\' or lower(email) like :like escape '\\')");
      params.put("like", like);
    }
    if (status != null && !status.isBlank()) {
      if (!status.matches("^(active|suspended|pending_deletion|deleted)$")) {
        throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown status filter.");
      }
      where.append(" and status = :status");
      params.put("status", status);
    }
    var count = jdbc.sql("select count(*) from identity.users" + where);
    var rows =
        jdbc.sql(
            "select id, username, email, email_verified, status, timezone, created_at, last_login_at from identity.users"
                + where
                + " order by created_at desc, id desc limit :limit offset :offset");
    for (var e : params.entrySet()) {
      count = count.param(e.getKey(), e.getValue());
      rows = rows.param(e.getKey(), e.getValue());
    }
    long total = count.query(Long.class).single();
    List<UserView> items =
        rows.param("limit", pageSize)
            .param("offset", offset)
            .query(
                (rs, i) ->
                    new UserView(
                        rs.getObject(1, UUID.class),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getBoolean(4),
                        rs.getString(5),
                        rs.getString(6),
                        rs.getTimestamp(7).toInstant(),
                        rs.getTimestamp(8).toInstant()))
            .list();
    return new UserPage(items, Math.max(0, page), pageSize, total);
  }

  @Transactional(readOnly = true)
  UserView get(UUID id) {
    UserAccount u = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found."));
    return view(u);
  }

  @Transactional
  UserView changeStatus(Authentication actor, UUID targetId, String newStatusDb, String reason) {
    UserRef caller = directory.current(actor);
    if (caller.id().equals(targetId)) {
      throw new ApiException(ErrorCode.FORBIDDEN, "You cannot change your own account status.");
    }
    UserAccount target =
        users.findById(targetId).orElseThrow(() -> ApiException.notFound("User not found."));
    roleAssignment.assertMayModerate(
        actor, target); // only a super admin may act on an administrator
    UserStatus old = target.status();
    UserStatus next = UserStatus.fromDb(newStatusDb);
    if (old == UserStatus.PENDING_DELETION || old == UserStatus.DELETED) {
      throw ApiException.conflict("This account is being deleted and cannot be changed.");
    }
    if (old == next) {
      throw ApiException.conflict("The account already has this status.");
    }
    target.changeStatus(next, clock.instant());
    users.flush();
    statusCache.evict(
        target.keycloakSub()); // takes effect immediately on this instance, <= 30 s on others
    audit.record(
        actor,
        caller.id(),
        AuditEvent.of("user.status.changed", "user", targetId.toString())
            .withChange(Map.of("status", old.db()), Map.of("status", next.db()))
            .withReason(reason));
    events.publish(
        "identity.user.status.changed",
        1,
        "user",
        targetId.toString(),
        Map.of("userId", targetId, "status", next.db()));
    return view(target);
  }

  // ------------------------------------------------------------------ grants

  @Transactional(readOnly = true)
  List<GrantView> grantsOf(UUID userId) {
    if (!users.existsById(userId)) {
      throw ApiException.notFound("User not found.");
    }
    return jdbc.sql(
            "select id, permission_code, resource_type, resource_id, granted_by, granted_at, expires_at from identity.scoped_grants where user_id = :u order by granted_at desc, id")
        .param("u", userId)
        .query(
            (rs, i) ->
                new GrantView(
                    rs.getObject(1, UUID.class),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getObject(5, UUID.class),
                    rs.getTimestamp(6).toInstant(),
                    rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toInstant()))
        .list();
  }

  @Transactional
  GrantView grant(Authentication actor, UUID targetId, GrantRequest r) {
    UserRef caller = directory.current(actor);
    UserAccount target =
        users.findById(targetId).orElseThrow(() -> ApiException.notFound("User not found."));
    if (target.status() != UserStatus.ACTIVE) {
      throw ApiException.conflict("Grants can only be given to active accounts.");
    }
    if (!catalog.snapshot().grantable().contains(r.permission())) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "This permission cannot be granted per resource.");
    }
    // Anti-escalation: nobody can hand out a capability they do not hold themselves (globally).
    if (!authz.can(actor, r.permission())) {
      throw new ApiException(ErrorCode.FORBIDDEN, "You cannot grant a permission you do not hold.");
    }
    Instant now = clock.instant();
    if (r.expiresAt() != null
        && (!r.expiresAt().isAfter(now) || r.expiresAt().isAfter(now.plus(MAX_GRANT_LIFETIME)))) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED,
          "expiresAt must be in the future and at most one year away.");
    }
    UUID id = Ids.newId();
    try {
      jdbc.sql(
              "insert into identity.scoped_grants (id, user_id, permission_code, resource_type, resource_id, granted_by, granted_at, expires_at) values (:id,:u,:p,:t,:r,:by,:at,:exp)")
          .param("id", id)
          .param("u", targetId)
          .param("p", r.permission())
          .param("t", r.resourceType())
          .param("r", r.resourceId())
          .param("by", caller.id())
          .param("at", Timestamp.from(now))
          .param("exp", r.expiresAt() == null ? null : Timestamp.from(r.expiresAt()))
          .update();
    } catch (DuplicateKeyException e) {
      throw ApiException.conflict("This grant already exists.");
    }
    Map<String, Object> after = new LinkedHashMap<>();
    after.put("permission", r.permission());
    after.put("resourceType", r.resourceType());
    after.put("resourceId", r.resourceId());
    after.put("expiresAt", r.expiresAt() == null ? null : r.expiresAt().toString());
    after.put("grantee", targetId.toString());
    audit.record(
        actor,
        caller.id(),
        AuditEvent.of("grant.created", "user", targetId.toString()).withChange(null, after));
    return new GrantView(
        id, r.permission(), r.resourceType(), r.resourceId(), caller.id(), now, r.expiresAt());
  }

  @Transactional
  void revoke(Authentication actor, UUID targetId, UUID grantId) {
    UserRef caller = directory.current(actor);
    var existing =
        jdbc.sql(
                "select permission_code, resource_type, resource_id from identity.scoped_grants where id = :id and user_id = :u")
            .param("id", grantId)
            .param("u", targetId)
            .query(
                (rs, i) ->
                    Map.<String, Object>of(
                        "permission",
                        rs.getString(1),
                        "resourceType",
                        rs.getString(2),
                        "resourceId",
                        rs.getString(3)))
            .optional()
            .orElseThrow(() -> ApiException.notFound("Grant not found."));
    jdbc.sql("delete from identity.scoped_grants where id = :id").param("id", grantId).update();
    audit.record(
        actor,
        caller.id(),
        AuditEvent.of("grant.revoked", "user", targetId.toString()).withChange(existing, null));
  }

  // ------------------------------------------------------------------ roles

  List<RoleView> roles() {
    List<RoleView> out = new ArrayList<>();
    catalog.snapshot().byRole().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            e ->
                out.add(
                    new RoleView(
                        e.getKey(),
                        e.getValue().global().stream().sorted().toList(),
                        e.getValue().scoped().stream().sorted().toList())));
    return out;
  }

  private static UserView view(UserAccount u) {
    return new UserView(
        u.id(),
        u.username(),
        u.email(),
        u.emailVerified(),
        u.status().db(),
        u.timezone(),
        u.createdAt(),
        u.lastLoginAt());
  }
}
