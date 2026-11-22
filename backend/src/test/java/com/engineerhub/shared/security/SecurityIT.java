package com.engineerhub.shared.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engineerhub.testsupport.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The real security filter chain + method security, with tokens injected (signature checks are
 * covered live against Keycloak).
 */
@IntegrationTest
class SecurityIT {

  @Autowired MockMvc mvc;

  /** A principal whose authorities are derived exactly like production (RealmRoleConverter). */
  private static JwtRequestPostProcessor user(String... realmRoles) {
    return jwt()
        .jwt(j -> j.subject("user-123").claim("realm_access", Map.of("roles", List.of(realmRoles))))
        .authorities(new RealmRoleConverter());
  }

  // ---- public vs protected ----

  @Test
  void pingIsPublic() throws Exception {
    mvc.perform(get("/api/v1/system/ping"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"));
  }

  @Test
  void protectedEndpointWithoutTokenIsA401ProblemWithChallenge() throws Exception {
    mvc.perform(get("/api/v1/system/whoami"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"))
        .andExpect(header().exists("X-Correlation-Id"))
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.type").value("urn:engineerhub:problem:unauthenticated"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.correlationId").isNotEmpty());
  }

  /**
   * The test profile points at an unreachable issuer, i.e. a simulated identity-provider outage.
   */
  @Test
  void identityProviderOutageIsA503ProblemWithRetryAfterNotA500() throws Exception {
    mvc.perform(get("/api/v1/system/whoami").header("Authorization", "Bearer some.jwt.value"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Retry-After", "5"))
        .andExpect(header().doesNotExist("WWW-Authenticate"))
        .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
        .andExpect(jsonPath("$.correlationId").isNotEmpty());
  }

  @Test
  void publicEndpointsWithoutATokenAreUnaffectedByTheOutage() throws Exception {
    mvc.perform(get("/api/v1/system/ping")).andExpect(status().isOk());
  }

  @Test
  void whoamiReturnsSubjectAndOnlyMappedStaffRoles() throws Exception {
    mvc.perform(
            get("/api/v1/system/whoami")
                .with(user("ADMIN", "MODERATOR", "offline_access", "PREMIUM_USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subject").value("user-123"))
        .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
        .andExpect(jsonPath("$.roles[1]").value("MODERATOR"))
        .andExpect(jsonPath("$.roles.length()").value(2));
  }

  // ---- role-based method security ----

  @Test
  void adminEndpointDeniesAuthenticatedUserWithoutRoleAs403Problem() throws Exception {
    mvc.perform(get("/api/v1/system/admin/ping").with(user()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.correlationId").isNotEmpty());
  }

  @Test
  void adminEndpointDeniesOtherStaffRoles() throws Exception {
    for (String role : List.of("INSTRUCTOR", "MODERATOR", "CONTENT_MANAGER", "PROBLEM_SETTER")) {
      mvc.perform(get("/api/v1/system/admin/ping").with(user(role)))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void adminEndpointAllowsAdminAndSuperAdmin() throws Exception {
    mvc.perform(get("/api/v1/system/admin/ping").with(user("ADMIN"))).andExpect(status().isOk());
    mvc.perform(get("/api/v1/system/admin/ping").with(user("SUPER_ADMIN")))
        .andExpect(status().isOk());
  }

  @Test
  void adminEndpointWithoutTokenIs401NotA403() throws Exception {
    mvc.perform(get("/api/v1/system/admin/ping"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  // ---- hardening ----

  @Test
  void responsesCarrySecurityHeaders() throws Exception {
    mvc.perform(get("/api/v1/system/ping"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(
            header()
                .string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
        .andExpect(header().exists("Permissions-Policy"))
        .andExpect(
            header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"));
  }

  @Test
  void noCorsHeadersAreEverEmittedWhenNoOriginIsConfigured() throws Exception {
    mvc.perform(get("/api/v1/system/ping").header("Origin", "https://evil.example"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    mvc.perform(
            options("/api/v1/system/ping")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
  }

  @Test
  void onlyHealthIsExposedByActuator() throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    for (String p :
        List.of("/actuator/env", "/actuator/beans", "/actuator/heapdump", "/actuator/info")) {
      mvc.perform(get(p)).andExpect(status().isNotFound());
    }
  }

  @Test
  void healthDoesNotLeakDetails() throws Exception {
    mvc.perform(get("/actuator/health").accept(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.components").doesNotExist())
        .andExpect(jsonPath("$.details").doesNotExist());
  }
}
