package com.engineerhub.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engineerhub.shared.id.Ids;
import com.engineerhub.testsupport.IntegrationTest;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/** Staff role assignment through the Keycloak admin API (fake provider), with its guard rails. */
@IntegrationTest
class RoleAssignmentIT {

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired ObjectMapper mapper;
  @Autowired FakeIdentityProvider idp;

  @BeforeEach
  void reset() {
    idp.reset();
  }

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

  private static String sub() {
    return "kc-" + Ids.newId();
  }

  private UUID provision(String sub, String... roles) throws Exception {
    var r =
        mvc.perform(get("/api/v1/me").with(token(sub, roles)))
            .andExpect(status().isOk())
            .andReturn();
    idp.give(sub, roles.length == 0 ? new String[0] : roles);
    return UUID.fromString(
        mapper.readTree(r.getResponse().getContentAsString()).get("id").asString());
  }

  private ResultActions setRoles(JwtRequestPostProcessor who, UUID target, String... roles)
      throws Exception {
    String list = roles.length == 0 ? "[]" : "[\"" + String.join("\",\"", roles) + "\"]";
    return mvc.perform(
        put("/api/v1/admin/users/" + target + "/roles")
            .with(who)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"roles\":" + list + ",\"reason\":\"role assignment test\"}"));
  }

  private long audits(UUID target) {
    return jdbc.sql(
            "select count(*) from admin.audit_logs where action = 'user.roles.changed' and resource_id = :r")
        .param("r", target.toString())
        .query(Long.class)
        .single();
  }

  @Test
  void everyoneWithoutThePermissionIsRefusedAndNothingChanges() throws Exception {
    String targetSub = sub();
    UUID target = provision(targetSub);
    for (String role :
        new String[] {"MODERATOR", "INSTRUCTOR", "PROBLEM_SETTER", "CONTENT_MANAGER"}) {
      setRoles(token(sub(), role), target, "MODERATOR").andExpect(status().isForbidden());
    }
    setRoles(token(sub()), target, "MODERATOR").andExpect(status().isForbidden()); // plain learner
    mvc.perform(
            put("/api/v1/admin/users/" + target + "/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"MODERATOR\"],\"reason\":\"anonymous attempt\"}"))
        .andExpect(status().isUnauthorized());
    assertThat(idp.roles.getOrDefault(targetSub, java.util.Set.of())).isEmpty();
    assertThat(audits(target)).isZero();
  }

  @Test
  void aSuperAdminAssignsAndRemovesRolesWithAuditAndEvent() throws Exception {
    String targetSub = sub();
    UUID target = provision(targetSub);
    var superAdmin = token(sub(), "SUPER_ADMIN");

    setRoles(superAdmin, target, "CONTENT_MANAGER", "MODERATOR")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles[0]").value("CONTENT_MANAGER"))
        .andExpect(jsonPath("$.roles[1]").value("MODERATOR"));
    assertThat(idp.roles.get(targetSub)).containsExactlyInAnyOrder("CONTENT_MANAGER", "MODERATOR");
    assertThat(audits(target)).isEqualTo(1);
    assertThat(
            jdbc.sql(
                    "select count(*) from shared.outbox where event_type = 'identity.user.roles.changed' and aggregate_id = :a")
                .param("a", target.toString())
                .query(Long.class)
                .single())
        .isEqualTo(1);

    setRoles(superAdmin, target, "MODERATOR").andExpect(status().isOk());
    assertThat(idp.roles.get(targetSub)).containsExactly("MODERATOR");
    setRoles(superAdmin, target)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles").isEmpty());
    assertThat(idp.roles.get(targetSub)).isEmpty();
    assertThat(audits(target)).isEqualTo(3);

    mvc.perform(get("/api/v1/admin/users/" + target + "/roles").with(superAdmin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles").isEmpty());
  }

  @Test
  void anUnchangedRoleSetIsANoOpWithoutAnAuditEntry() throws Exception {
    String targetSub = sub();
    UUID target = provision(targetSub, "MODERATOR");
    setRoles(token(sub(), "SUPER_ADMIN"), target, "MODERATOR").andExpect(status().isOk());
    assertThat(audits(target)).isZero();
  }

  @Test
  void anAdminCanManageLowerRolesButNeverAdministratorRoles() throws Exception {
    var admin = token(sub(), "ADMIN");
    String learnerSub = sub();
    UUID learner = provision(learnerSub);

    setRoles(admin, learner, "INSTRUCTOR").andExpect(status().isOk());
    assertThat(idp.roles.get(learnerSub)).containsExactly("INSTRUCTOR");

    // escalation attempts
    setRoles(admin, learner, "INSTRUCTOR", "ADMIN").andExpect(status().isForbidden());
    setRoles(admin, learner, "SUPER_ADMIN").andExpect(status().isForbidden());
    assertThat(idp.roles.get(learnerSub)).containsExactly("INSTRUCTOR");

    // an administrator's account is out of reach for an admin (roles and status)
    String otherAdminSub = sub();
    UUID otherAdmin = provision(otherAdminSub, "ADMIN");
    setRoles(admin, otherAdmin, "MODERATOR").andExpect(status().isForbidden());
    setRoles(admin, otherAdmin).andExpect(status().isForbidden());
    assertThat(idp.roles.get(otherAdminSub)).containsExactly("ADMIN");
    mvc.perform(
            put("/api/v1/admin/users/" + otherAdmin + "/status")
                .with(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"suspended\",\"reason\":\"admin attacking admin\"}"))
        .andExpect(status().isForbidden());
    assertThat(
            jdbc.sql("select status from identity.users where id = :i")
                .param("i", otherAdmin)
                .query(String.class)
                .single())
        .isEqualTo("active");

    // the super admin can
    mvc.perform(
            put("/api/v1/admin/users/" + otherAdmin + "/status")
                .with(token(sub(), "SUPER_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"suspended\",\"reason\":\"owner suspends an admin\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void nobodyCanChangeTheirOwnRoles() throws Exception {
    String mySub = sub();
    var me = token(mySub, "SUPER_ADMIN");
    UUID myId = provision(mySub, "SUPER_ADMIN");
    setRoles(me, myId, "SUPER_ADMIN", "ADMIN").andExpect(status().isForbidden());
    setRoles(me, myId).andExpect(status().isForbidden());
  }

  @Test
  void theLastSuperAdminCannotBeRemoved() throws Exception {
    // Only one holder exists in the fake provider.
    String onlySub = sub();
    UUID only = provision(onlySub, "SUPER_ADMIN");
    var other =
        token(sub(), "SUPER_ADMIN"); // the caller's own roles are claims, not provider state
    setRoles(other, only, "ADMIN").andExpect(status().isConflict());
    assertThat(idp.roles.get(onlySub)).containsExactly("SUPER_ADMIN");

    // With a second holder it is allowed.
    idp.give(sub(), "SUPER_ADMIN");
    setRoles(other, only, "ADMIN").andExpect(status().isOk());
    assertThat(idp.roles.get(onlySub)).containsExactly("ADMIN");
  }

  @Test
  void invalidRequestsAreRejected() throws Exception {
    UUID target = provision(sub());
    var superAdmin = token(sub(), "SUPER_ADMIN");
    setRoles(superAdmin, target, "NOT_A_ROLE").andExpect(status().isBadRequest());
    setRoles(superAdmin, target, "lowercase").andExpect(status().is4xxClientError());
    // roles that are not staff roles (e.g. the MFA marker) cannot be assigned
    setRoles(superAdmin, target, "STAFF_MFA").andExpect(status().isBadRequest());
    mvc.perform(
            put("/api/v1/admin/users/" + target + "/roles")
                .with(superAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"MODERATOR\"],\"reason\":\"x\"}"))
        .andExpect(status().is4xxClientError()); // reason too short
    setRoles(superAdmin, UUID.randomUUID(), "MODERATOR").andExpect(status().isNotFound());
    assertThat(audits(target)).isZero();
  }

  @Test
  void whenTheIdentityProviderIsDownNothingChangesAndNothingIsAudited() throws Exception {
    String targetSub = sub();
    UUID target = provision(targetSub);
    idp.down = true;
    setRoles(token(sub(), "SUPER_ADMIN"), target, "MODERATOR")
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    assertThat(audits(target)).isZero();
    idp.down = false;
    assertThat(idp.roles.getOrDefault(targetSub, java.util.Set.of())).isEmpty();
  }

  @Test
  void withoutAdminCredentialsRoleManagementIsRefused() throws Exception {
    UUID target = provision(sub());
    idp.configured = false;
    setRoles(token(sub(), "SUPER_ADMIN"), target, "MODERATOR")
        .andExpect(status().isServiceUnavailable());
    mvc.perform(get("/api/v1/admin/users/" + target + "/roles").with(token(sub(), "ADMIN")))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void anAdminSuspendingAnOrdinaryUserStillWorksAndFailsClosedIfTheProviderIsDown()
      throws Exception {
    UUID learner = provision(sub());
    var admin = token(sub(), "ADMIN");
    idp.down = true;
    mvc.perform(
            put("/api/v1/admin/users/" + learner + "/status")
                .with(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"suspended\",\"reason\":\"provider is down\"}"))
        .andExpect(status().isServiceUnavailable());
    idp.down = false;
    mvc.perform(
            put("/api/v1/admin/users/" + learner + "/status")
                .with(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"suspended\",\"reason\":\"provider is back\"}"))
        .andExpect(status().isOk());
  }
}
