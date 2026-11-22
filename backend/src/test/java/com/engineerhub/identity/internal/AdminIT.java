package com.engineerhub.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engineerhub.identity.Authorizer;
import com.engineerhub.shared.id.Ids;
import com.engineerhub.testsupport.IntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * Admin API: authorization of every endpoint, account status, scoped grants, anti-escalation,
 * audit.
 */
@IntegrationTest
class AdminIT {

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired ObjectMapper mapper;
  @Autowired Authorizer authz;
  @Autowired PermissionCatalog catalog;

  private static JwtRequestPostProcessor token(String sub, String... roles) {
    return jwt()
        .jwt(
            j ->
                j.subject(sub)
                    .claim("preferred_username", sub)
                    .claim("email", sub + "@example.test"))
        .authorities(
            Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toArray(SimpleGrantedAuthority[]::new));
  }

  private static String newSub() {
    return "kc-" + Ids.newId();
  }

  /** Provisions a user via /me and returns its local id. */
  private UUID provision(String sub, String... roles) throws Exception {
    MvcResult r =
        mvc.perform(get("/api/v1/me").with(token(sub, roles)))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(
        mapper.readTree(r.getResponse().getContentAsString()).get("id").asString());
  }

  private ResultActions call(
      MockHttpServletRequestBuilder b, JwtRequestPostProcessor who, String body) throws Exception {
    MockHttpServletRequestBuilder req = who == null ? b : b.with(who);
    return mvc.perform(
        body == null ? req : req.contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private long auditCount(String action, UUID resource) {
    return jdbc.sql("select count(*) from admin.audit_logs where action = :a and resource_id = :r")
        .param("a", action)
        .param("r", resource.toString())
        .query(Long.class)
        .single();
  }

  // ------------------------------------------------------------------ authorization of every
  // endpoint

  @Test
  void everyAdminEndpointEnforcesItsPermissionForEveryRole() throws Exception {
    UUID target = provision(newSub());
    UUID grantId = UUID.randomUUID();
    String suspendBody = "{\"status\":\"suspended\",\"reason\":\"authorization table test\"}";
    String grantBody =
        "{\"permission\":\"course.edit\",\"resourceType\":\"course\",\"resourceId\":\"auth-table\"}";

    record Case(
        String name, MockHttpServletRequestBuilder req, String body, List<String> allowed) {}
    List<String> readers = List.of("ADMIN", "SUPER_ADMIN");
    List<String> noRoles = List.of();
    // allowed roles succeed (any 2xx/4xx other than 401/403); everybody else must get 403 (or 401
    // when anonymous)
    List<Case> cases =
        List.of(
            new Case("list users", get("/api/v1/admin/users"), null, readers),
            new Case("get user", get("/api/v1/admin/users/" + target), null, readers),
            new Case(
                "list grants", get("/api/v1/admin/users/" + target + "/grants"), null, readers),
            new Case("roles", get("/api/v1/admin/roles"), null, readers),
            new Case(
                "suspend", put("/api/v1/admin/users/" + target + "/status"), suspendBody, readers),
            new Case(
                "grant", post("/api/v1/admin/users/" + target + "/grants"), grantBody, readers),
            new Case(
                "revoke",
                delete("/api/v1/admin/users/" + target + "/grants/" + grantId),
                null,
                readers),
            new Case("audit search", get("/api/v1/admin/audit-logs"), null, readers),
            new Case("audit verify", get("/api/v1/admin/audit-logs/verify"), null, readers));
    String[] roles = {
      "CONTENT_MANAGER", "MODERATOR", "INSTRUCTOR", "PROBLEM_SETTER", "ADMIN", "SUPER_ADMIN"
    };

    for (Case c : cases) {
      assertThat(call(c.req(), null, c.body()).andReturn().getResponse().getStatus())
          .as("%s: anonymous", c.name())
          .isEqualTo(401);
      assertThat(call(c.req(), token(newSub()), c.body()).andReturn().getResponse().getStatus())
          .as("%s: no role", c.name())
          .isEqualTo(403);
      for (String role : roles) {
        int code =
            call(c.req(), token(newSub(), role), c.body()).andReturn().getResponse().getStatus();
        if (c.allowed().contains(role)) {
          assertThat(code).as("%s: %s must be allowed", c.name(), role).isNotIn(401, 403);
        } else {
          assertThat(code).as("%s: %s must be denied", c.name(), role).isEqualTo(403);
        }
      }
    }
    assertThat(noRoles).isEmpty();
  }

  // ------------------------------------------------------------------ listing

  @Test
  void usersCanBeSearchedPagedAndFilteredSafely() throws Exception {
    String tag = "zq" + Long.toString(System.nanoTime(), 36);
    for (int i = 0; i < 3; i++) {
      provision(newSub()); // noise
    }
    UUID a = provision(tag + "-alpha");
    provision(tag + "-beta");
    JwtRequestPostProcessor admin = token(newSub(), "ADMIN");

    mvc.perform(get("/api/v1/admin/users").param("q", tag.toUpperCase()).with(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.items.length()").value(2));
    mvc.perform(get("/api/v1/admin/users").param("q", tag + "-alp").with(admin))
        .andExpect(jsonPath("$.items[0].id").value(a.toString()));
    mvc.perform(get("/api/v1/admin/users").param("q", "%").with(admin))
        .andExpect(jsonPath("$.total").value(0)); // wildcard is literal
    mvc.perform(get("/api/v1/admin/users").param("q", "_").with(admin))
        .andExpect(jsonPath("$.total").value(0));
    mvc.perform(get("/api/v1/admin/users").param("q", "' OR 1=1 --").with(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
    mvc.perform(get("/api/v1/admin/users").param("size", "100000").with(admin))
        .andExpect(jsonPath("$.size").value(100));
    mvc.perform(get("/api/v1/admin/users").param("size", "2").param("page", "1").with(admin))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.page").value(1));
    mvc.perform(get("/api/v1/admin/users").param("status", "bogus").with(admin))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/admin/users").param("status", "suspended").param("q", tag).with(admin))
        .andExpect(jsonPath("$.total").value(0));
    mvc.perform(get("/api/v1/admin/users/" + UUID.randomUUID()).with(admin))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/admin/users/not-a-uuid").with(admin))
        .andExpect(status().isBadRequest());
  }

  // ------------------------------------------------------------------ account status

  @Test
  void suspendingAnAccountBlocksItImmediatelyIsAuditedAndCanBeUndone() throws Exception {
    String sub = newSub();
    UUID target = provision(sub);
    String adminSub = newSub();
    JwtRequestPostProcessor admin = token(adminSub, "ADMIN");

    call(
            put("/api/v1/admin/users/" + target + "/status"),
            admin,
            "{\"status\":\"suspended\",\"reason\":\"Spam posting reported\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("suspended"));

    mvc.perform(get("/api/v1/me").with(token(sub)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED")); // no waiting for cache expiry
    var row =
        jdbc.sql(
                "select actor_sub, actor_roles, details_before, details_after, reason, correlation_id from admin.audit_logs where action = 'user.status.changed' and resource_id = :r")
            .param("r", target.toString())
            .query(
                (rs, i) ->
                    List.of(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5),
                        String.valueOf(rs.getString(6))))
            .single();
    assertThat(row.get(0)).isEqualTo(adminSub);
    assertThat(row.get(1)).isEqualTo("ADMIN");
    assertThat(row.get(2)).isEqualTo("{\"status\":\"active\"}");
    assertThat(row.get(3)).isEqualTo("{\"status\":\"suspended\"}");
    assertThat(row.get(4)).isEqualTo("Spam posting reported");
    assertThat(String.join("", row))
        .doesNotContain("example.test"); // no email/PII in the audit trail
    assertThat(
            jdbc.sql(
                    "select count(*) from shared.outbox where event_type = 'identity.user.status.changed' and payload->>'userId' = :u")
                .param("u", target.toString())
                .query(Long.class)
                .single())
        .isEqualTo(1);

    call(
            put("/api/v1/admin/users/" + target + "/status"),
            admin,
            "{\"status\":\"suspended\",\"reason\":\"duplicate request\"}")
        .andExpect(status().isConflict());
    call(
            put("/api/v1/admin/users/" + target + "/status"),
            admin,
            "{\"status\":\"active\",\"reason\":\"Appeal accepted\"}")
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/me").with(token(sub))).andExpect(status().isOk());
    assertThat(auditCount("user.status.changed", target)).isEqualTo(2);
  }

  @Test
  void statusChangeRulesAndValidation() throws Exception {
    String adminSub = newSub();
    UUID self = provision(adminSub, "ADMIN");
    JwtRequestPostProcessor admin = token(adminSub, "ADMIN");
    UUID other = provision(newSub());

    call(
            put("/api/v1/admin/users/" + self + "/status"),
            admin,
            "{\"status\":\"suspended\",\"reason\":\"locking myself out\"}")
        .andExpect(status().isForbidden()); // never self
    call(
            put("/api/v1/admin/users/" + other + "/status"),
            admin,
            "{\"status\":\"suspended\",\"reason\":\"x\"}")
        .andExpect(status().isBadRequest()); // reason required
    call(
            put("/api/v1/admin/users/" + other + "/status"),
            admin,
            "{\"status\":\"deleted\",\"reason\":\"not allowed here\"}")
        .andExpect(status().isBadRequest());
    call(put("/api/v1/admin/users/" + other + "/status"), admin, "{\"status\":\"suspended\"}")
        .andExpect(status().isBadRequest());
    call(
            put("/api/v1/admin/users/" + UUID.randomUUID() + "/status"),
            admin,
            "{\"status\":\"suspended\",\"reason\":\"unknown user\"}")
        .andExpect(status().isNotFound());
    jdbc.sql("update identity.users set status = 'deleted' where id = :id")
        .param("id", other)
        .update();
    call(
            put("/api/v1/admin/users/" + other + "/status"),
            admin,
            "{\"status\":\"active\",\"reason\":\"resurrect attempt\"}")
        .andExpect(status().isConflict());
    assertThat(auditCount("user.status.changed", other))
        .as("rejected changes leave no audit entry")
        .isZero();
  }

  // ------------------------------------------------------------------ grants

  @Test
  void adminsGrantAndRevokeScopedPermissionsAndTheyTakeEffect() throws Exception {
    String sub = newSub();
    UUID target = provision(sub, "INSTRUCTOR");
    JwtRequestPostProcessor admin = token(newSub(), "ADMIN");
    var instructor =
        new JwtAuthenticationToken(
            Jwt.withTokenValue("t").header("alg", "none").subject(sub).build(),
            List.of(new SimpleGrantedAuthority("ROLE_INSTRUCTOR")),
            sub);
    assertThat(authz.can(instructor, "course.edit", "course", "graphs-101")).isFalse();

    MvcResult created =
        call(
                post("/api/v1/admin/users/" + target + "/grants"),
                admin,
                "{\"permission\":\"course.edit\",\"resourceType\":\"course\",\"resourceId\":\"graphs-101\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.permission").value("course.edit"))
            .andReturn();
    UUID grantId =
        UUID.fromString(
            mapper.readTree(created.getResponse().getContentAsString()).get("id").asString());
    assertThat(authz.can(instructor, "course.edit", "course", "graphs-101")).isTrue();
    assertThat(authz.can(instructor, "course.edit", "course", "other")).isFalse();
    call(get("/api/v1/admin/users/" + target + "/grants"), admin, null)
        .andExpect(jsonPath("$.length()").value(1));
    assertThat(auditCount("grant.created", target)).isEqualTo(1);

    call(delete("/api/v1/admin/users/" + target + "/grants/" + grantId), admin, null)
        .andExpect(status().isNoContent());
    assertThat(authz.can(instructor, "course.edit", "course", "graphs-101")).isFalse();
    call(delete("/api/v1/admin/users/" + target + "/grants/" + grantId), admin, null)
        .andExpect(status().isNotFound());
    assertThat(auditCount("grant.revoked", target)).isEqualTo(1);
  }

  @Test
  void grantRulesAreEnforced() throws Exception {
    UUID target = provision(newSub(), "INSTRUCTOR");
    JwtRequestPostProcessor admin = token(newSub(), "ADMIN");
    String path = "/api/v1/admin/users/" + target + "/grants";
    String ok = "{\"permission\":\"course.edit\",\"resourceType\":\"course\",\"resourceId\":\"c1\"";

    call(
            post(path),
            admin,
            "{\"permission\":\"course.publish\",\"resourceType\":\"course\",\"resourceId\":\"c1\"}")
        .andExpect(status().isBadRequest()); // not grantable per resource
    call(
            post(path),
            admin,
            "{\"permission\":\"user.suspend\",\"resourceType\":\"course\",\"resourceId\":\"c1\"}")
        .andExpect(status().isBadRequest());
    call(
            post(path),
            admin,
            "{\"permission\":\"nope.nope\",\"resourceType\":\"course\",\"resourceId\":\"c1\"}")
        .andExpect(status().isBadRequest());
    call(
            post(path),
            admin,
            "{\"permission\":\"course.edit\",\"resourceType\":\"Course!\",\"resourceId\":\"c1\"}")
        .andExpect(status().isBadRequest());
    call(
            post(path),
            admin,
            "{\"permission\":\"course.edit\",\"resourceType\":\"course\",\"resourceId\":\"../etc\"}")
        .andExpect(status().isBadRequest());
    call(
            post(path),
            admin,
            ok + ",\"expiresAt\":\"" + Instant.now().minus(1, ChronoUnit.DAYS) + "\"}")
        .andExpect(status().isBadRequest());
    call(
            post(path),
            admin,
            ok + ",\"expiresAt\":\"" + Instant.now().plus(400, ChronoUnit.DAYS) + "\"}")
        .andExpect(status().isBadRequest());
    call(
            post(path),
            admin,
            ok + ",\"expiresAt\":\"" + Instant.now().plus(30, ChronoUnit.DAYS) + "\"}")
        .andExpect(status().isCreated());
    call(post(path), admin, ok + "}")
        .andExpect(status().isConflict()); // same user/permission/resource again
    call(post("/api/v1/admin/users/" + UUID.randomUUID() + "/grants"), admin, ok + "}")
        .andExpect(status().isNotFound());

    jdbc.sql("update identity.users set status = 'suspended' where id = :id")
        .param("id", target)
        .update();
    call(
            post(path),
            admin,
            "{\"permission\":\"course.edit\",\"resourceType\":\"course\",\"resourceId\":\"c2\"}")
        .andExpect(status().isConflict()); // suspended accounts get no new grants
  }

  @Test
  void nobodyCanGrantWhatTheyDoNotHold() throws Exception {
    UUID target = provision(newSub(), "INSTRUCTOR");
    // simulate an ADMIN whose problem.edit capability was taken away
    jdbc.sql(
            "delete from identity.role_permissions where role = 'ADMIN' and permission_code = 'problem.edit'")
        .update();
    catalog.refresh();
    try {
      call(
              post("/api/v1/admin/users/" + target + "/grants"),
              token(newSub(), "ADMIN"),
              "{\"permission\":\"problem.edit\",\"resourceType\":\"problem\",\"resourceId\":\"p1\"}")
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.detail").value("You cannot grant a permission you do not hold."));
      call(
              post("/api/v1/admin/users/" + target + "/grants"),
              token(newSub(), "SUPER_ADMIN"),
              "{\"permission\":\"problem.edit\",\"resourceType\":\"problem\",\"resourceId\":\"p1\"}")
          .andExpect(status().isCreated());
    } finally {
      jdbc.sql(
              "insert into identity.role_permissions (role, permission_code, scoped) values ('ADMIN','problem.edit',false) on conflict do nothing")
          .update();
      catalog.refresh();
    }
  }

  @Test
  void rolesEndpointListsTheSeededMatrix() throws Exception {
    call(get("/api/v1/admin/roles"), token(newSub(), "ADMIN"), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(6))
        .andExpect(jsonPath("$[?(@.role=='INSTRUCTOR')].scoped[0]").value("content.premium.read"));
    assertThat(Map.of()).isEmpty();
  }
}
