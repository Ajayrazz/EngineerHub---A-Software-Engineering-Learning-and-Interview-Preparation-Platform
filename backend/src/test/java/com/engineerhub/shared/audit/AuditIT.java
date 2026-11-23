package com.engineerhub.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engineerhub.shared.id.Ids;
import com.engineerhub.testsupport.IntegrationTest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Append-only, tamper-evident audit log. */
@IntegrationTest
class AuditIT {

  @Autowired AuditLog audit;
  @Autowired AuditVerifier verifier;
  @Autowired JdbcClient jdbc;
  @Autowired MockMvc mvc;
  @Autowired PlatformTransactionManager txm;

  private static Authentication actor(String sub, String... roles) {
    Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(sub).build();
    return new JwtAuthenticationToken(
        jwt,
        java.util.Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList(),
        sub);
  }

  private void record(String action, String resource) {
    new TransactionTemplate(txm)
        .executeWithoutResult(
            s ->
                audit.record(
                    actor("kc-audit", "ADMIN"),
                    null,
                    AuditEvent.of(action, "test", resource)
                        .withChange(Map.of("a", 1), Map.of("a", 2))
                        .withReason("because")));
  }

  @Test
  void recordingRequiresATransactionAndCompleteInput() {
    assertThatThrownBy(() -> audit.record(actor("x"), null, AuditEvent.of("a.b", "t", "1")))
        .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    // each invalid call in its own transaction (an exception through the proxy marks its
    // transaction rollback-only)
    for (Runnable invalid :
        new Runnable[] {
          () -> audit.record(null, null, AuditEvent.of("a.b", "t", "1")),
          () -> audit.record(actor("x"), null, AuditEvent.of(" ", "t", "1")),
          () -> audit.record(actor("x"), null, AuditEvent.of("a.b", "t", null))
        }) {
      assertThatThrownBy(
              () -> new TransactionTemplate(txm).executeWithoutResult(s -> invalid.run()))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  /**
   * Regression: on Linux the JVM clock has nanosecond precision, PostgreSQL stores microseconds.
   * Unless the timestamp is truncated before hashing, the chain "verifies" fine on macOS
   * (microsecond clock) and breaks on every Linux/CI run. A nanosecond-precision clock reproduces
   * it on any OS.
   */
  @Test
  void nanosecondPrecisionClocksDoNotBreakTheChain() {
    java.time.Clock nanos =
        java.time.Clock.fixed(
            java.time.Instant.parse("2026-11-23T01:02:03.123456789Z"), java.time.ZoneOffset.UTC);
    AuditLogImpl withNanos = new AuditLogImpl(jdbc, nanos);
    String resource = "nanos-" + Ids.newId();
    new TransactionTemplate(txm)
        .executeWithoutResult(
            s ->
                withNanos.record(
                    actor("kc-audit", "ADMIN"),
                    null,
                    AuditEvent.of("test.nanos", "test", resource)));
    assertThat(verifier.verify().ok()).isTrue();
  }

  @Test
  void anEntryExistsIfAndOnlyIfTheSurroundingTransactionCommits() {
    String resource = "rollback-" + Ids.newId();
    assertThatThrownBy(
            () ->
                new TransactionTemplate(txm)
                    .executeWithoutResult(
                        s -> {
                          audit.record(
                              actor("kc-audit", "ADMIN"),
                              null,
                              AuditEvent.of("test.rolled.back", "test", resource));
                          throw new IllegalStateException("the audited action failed");
                        }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            jdbc.sql("select count(*) from admin.audit_logs where resource_id = :r")
                .param("r", resource)
                .query(Long.class)
                .single())
        .isZero();
  }

  @Test
  void historyCannotBeUpdatedDeletedOrTruncated() {
    String resource = "immutable-" + Ids.newId();
    record("test.immutable", resource);
    for (String sql :
        new String[] {
          "update admin.audit_logs set action = 'x' where resource_id = '" + resource + "'",
          "delete from admin.audit_logs where resource_id = '" + resource + "'",
          "truncate admin.audit_logs"
        }) {
      assertThatThrownBy(() -> jdbc.sql(sql).update()).as(sql).hasMessageContaining("append-only");
    }
    assertThat(
            jdbc.sql("select count(*) from admin.audit_logs where resource_id = :r")
                .param("r", resource)
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void theChainVerifiesAndDetectsModificationAndDeletion() {
    for (int i = 0; i < 3; i++) record("test.chain", "chain-" + i);
    assertThat(verifier.verify().ok()).isTrue();
    assertThat(verifier.verify().checked()).isGreaterThanOrEqualTo(3);

    // Tamper INSIDE a transaction that is rolled back, so the shared chain stays intact for other
    // tests.
    String lastId =
        jdbc.sql("select max(id)::text from admin.audit_logs").query(String.class).single();
    new TransactionTemplate(txm)
        .executeWithoutResult(
            s -> {
              jdbc.sql("alter table admin.audit_logs disable trigger audit_logs_no_update_delete")
                  .update();
              jdbc.sql("update admin.audit_logs set reason = 'rewritten history' where id = :id")
                  .param("id", Long.parseLong(lastId))
                  .update();
              var edited = verifier.verify();
              assertThat(edited.ok()).isFalse();
              assertThat(edited.firstBadId()).isEqualTo(Long.parseLong(lastId));
              assertThat(edited.problem()).contains("modified");
              s.setRollbackOnly();
            });
    new TransactionTemplate(txm)
        .executeWithoutResult(
            s -> {
              jdbc.sql("alter table admin.audit_logs disable trigger audit_logs_no_update_delete")
                  .update();
              jdbc.sql(
                      "delete from admin.audit_logs where id = (select min(id) + 1 from admin.audit_logs)")
                  .update(); // remove a row in the middle
              var removed = verifier.verify();
              assertThat(removed.ok()).isFalse();
              assertThat(removed.problem()).contains("chain link broken");
              s.setRollbackOnly();
            });
    assertThat(verifier.verify().ok()).as("rolled back: chain intact again").isTrue();
  }

  @Test
  void parallelWritersKeepASingleLinearChain() throws Exception {
    long before = jdbc.sql("select count(*) from admin.audit_logs").query(Long.class).single();
    ExecutorService pool = Executors.newFixedThreadPool(12);
    try {
      List<Future<?>> fs = new ArrayList<>();
      for (int i = 0; i < 40; i++) {
        int n = i;
        fs.add(pool.submit(() -> record("test.parallel", "par-" + n)));
      }
      for (Future<?> f : fs) f.get();
    } finally {
      pool.shutdownNow();
    }
    assertThat(jdbc.sql("select count(*) from admin.audit_logs").query(Long.class).single())
        .isEqualTo(before + 40);
    Set<String> prevs =
        new HashSet<>(
            jdbc.sql("select encode(prev_hash,'hex') from admin.audit_logs")
                .query(String.class)
                .list());
    assertThat(prevs)
        .as("every row links to a distinct predecessor (no fork)")
        .hasSize((int) (before + 40));
    assertThat(verifier.verify().ok()).isTrue();
  }

  @Test
  void requestMetadataIsCapturedAndSanitized() throws Exception {
    // via the real admin endpoint so a request context exists
    String sub = "kc-" + Ids.newId();
    mvc.perform(
            get("/api/v1/me").with(jwt().jwt(j -> j.subject(sub).claim("preferred_username", sub))))
        .andExpect(status().isOk());
    String id =
        jdbc.sql("select id::text from identity.users where keycloak_sub = :s")
            .param("s", sub)
            .query(String.class)
            .single();
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/v1/admin/users/" + id + "/status")
                .header("X-Forwarded-For", "203.0.113.9, 10.0.0.1")
                .header("User-Agent", "Mozilla/5.0 " + "x".repeat(500))
                .contentType("application/json")
                .content("{\"status\":\"suspended\",\"reason\":\"metadata test\"}")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("kc-meta-admin")
                                    .claim("preferred_username", "meta-admin"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isOk());
    var meta =
        jdbc.sql(
                "select forwarded_for, user_agent from admin.audit_logs where resource_id = :r and action = 'user.status.changed'")
            .param("r", id)
            .query((rs, i) -> List.of(rs.getString(1), rs.getString(2)))
            .single();
    assertThat(meta.get(0)).isEqualTo("203.0.113.9, 10.0.0.1");
    assertThat(meta.get(1))
        .hasSize(300)
        .startsWith(
            "Mozilla/5.0 "); // clipped; control chars never get this far (the HTTP firewall rejects
    // them)
  }

  @Test
  void auditLogSearchIsRestrictedFilteredAndInjectionSafe() throws Exception {
    String resource = "search-" + Ids.newId();
    record("test.search", resource);
    var admin =
        jwt()
            .jwt(j -> j.subject("kc-searcher"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    mvc.perform(get("/api/v1/admin/audit-logs").param("resourceId", resource).with(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].action").value("test.search"))
        .andExpect(jsonPath("$.items[0].reason").value("because"));
    mvc.perform(get("/api/v1/admin/audit-logs").param("action", "' OR '1'='1").with(admin))
        .andExpect(jsonPath("$.items.length()").value(0));
    mvc.perform(get("/api/v1/admin/audit-logs").param("size", "5000").with(admin))
        .andExpect(jsonPath("$.size").value(100));
    mvc.perform(get("/api/v1/admin/audit-logs/verify").with(admin))
        .andExpect(jsonPath("$.ok").value(true));
    mvc.perform(
            get("/api/v1/admin/audit-logs")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR"))))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/audit-logs")).andExpect(status().isUnauthorized());
  }
}
