package com.engineerhub.shared.audit;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Walks the whole chain and recomputes every hash: any edit, deletion or reordering shows up. */
@Service
public class AuditVerifier {

  public record Result(boolean ok, long checked, Long firstBadId, String problem) {}

  private final JdbcClient jdbc;

  AuditVerifier(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public Result verify() {
    byte[][] prev = {AuditLogImpl.GENESIS};
    long[] checked = {0};
    Long[] bad = {null};
    String[] why = {null};
    jdbc.sql(
            "select id, occurred_at, actor_id, actor_sub, actor_roles, action, resource_type, resource_id, details_before, details_after, reason, correlation_id, prev_hash, hash from admin.audit_logs order by id")
        .query(
            rs -> {
              if (bad[0] != null) {
                return;
              }
              long id = rs.getLong("id");
              byte[] storedPrev = rs.getBytes("prev_hash");
              byte[] storedHash = rs.getBytes("hash");
              UUID actor = rs.getObject("actor_id", UUID.class);
              Instant at = rs.getTimestamp("occurred_at").toInstant();
              byte[] expected =
                  AuditLogImpl.hash(
                      prev[0],
                      at,
                      actor,
                      rs.getString("actor_sub"),
                      rs.getString("actor_roles"),
                      rs.getString("action"),
                      rs.getString("resource_type"),
                      rs.getString("resource_id"),
                      rs.getString("details_before"),
                      rs.getString("details_after"),
                      rs.getString("reason"),
                      rs.getString("correlation_id"));
              if (!AuditLogImpl.same(storedPrev, prev[0])) {
                bad[0] = id;
                why[0] = "chain link broken (a row before this one was removed or reordered)";
              } else if (!AuditLogImpl.same(storedHash, expected)) {
                bad[0] = id;
                why[0] = "row content does not match its hash (modified)";
              }
              prev[0] = storedHash;
              checked[0]++;
            });
    return new Result(bad[0] == null, checked[0], bad[0], why[0]);
  }
}
