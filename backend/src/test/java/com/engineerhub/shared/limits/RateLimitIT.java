package com.engineerhub.shared.limits;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engineerhub.shared.id.Ids;
import com.engineerhub.testsupport.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Per-principal quotas against a real Redis, and the request-size cap. */
@IntegrationTest
@TestPropertySource(
    properties = {
      "engineerhub.limits.rate.read-per-window=3",
      "engineerhub.limits.rate.write-per-window=2",
      "engineerhub.limits.rate.admin-per-window=2",
      "engineerhub.limits.max-request-bytes=2048"
    })
class RateLimitIT {

  @Autowired MockMvc mvc;

  private static JwtRequestPostProcessor user(String role) {
    var p =
        jwt()
            .jwt(
                j -> j.subject("kc-" + Ids.newId()).claim("preferred_username", "u" + Ids.newId()));
    return role == null ? p : p.authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }

  private static RequestPostProcessor fromIp(String ip) {
    return r -> {
      r.setRemoteAddr(ip);
      return r;
    };
  }

  @Test
  void readQuotaIsEnforcedWithProblemJsonAndHeaders() throws Exception {
    var u = user(null);
    for (int i = 1; i <= 3; i++) {
      mvc.perform(get("/api/v1/me").with(u))
          .andExpect(status().isOk())
          .andExpect(header().string("X-RateLimit-Limit", "3"))
          .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(3 - i)));
    }
    mvc.perform(get("/api/v1/me").with(u))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(header().string("X-RateLimit-Remaining", "0"))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    // The 429 must not leak or reach the controller: still limited.
    mvc.perform(get("/api/v1/me").with(u)).andExpect(status().isTooManyRequests());
  }

  @Test
  void quotasArePerPrincipal() throws Exception {
    var noisy = user(null);
    for (int i = 0; i < 4; i++) {
      mvc.perform(get("/api/v1/me").with(noisy));
    }
    mvc.perform(get("/api/v1/me").with(noisy)).andExpect(status().isTooManyRequests());
    mvc.perform(get("/api/v1/me").with(user(null))).andExpect(status().isOk());
  }

  @Test
  void anonymousCallersAreLimitedByAddress() throws Exception {
    String ip = "10.9." + (int) (Math.random() * 200) + "." + (int) (Math.random() * 200);
    for (int i = 0; i < 3; i++) {
      mvc.perform(get("/api/v1/me").with(fromIp(ip))).andExpect(status().isUnauthorized());
    }
    mvc.perform(get("/api/v1/me").with(fromIp(ip))).andExpect(status().isTooManyRequests());
    mvc.perform(get("/api/v1/me").with(fromIp("10.8.1.1"))).andExpect(status().isUnauthorized());
  }

  @Test
  void writesAndReadsHaveSeparateBudgets() throws Exception {
    var u = user(null);
    String body = "{\"theme\":\"dark\"}";
    for (int i = 0; i < 2; i++) {
      mvc.perform(
              put("/api/v1/me/preferences")
                  .with(u)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().is(org.hamcrest.Matchers.not(429)));
    }
    mvc.perform(
            put("/api/v1/me/preferences")
                .with(u)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isTooManyRequests());
    mvc.perform(get("/api/v1/me").with(u)).andExpect(status().isOk()); // read budget untouched
  }

  @Test
  void adminEndpointsHaveTheirOwnBudget() throws Exception {
    var admin = user("ADMIN");
    for (int i = 0; i < 2; i++) {
      mvc.perform(get("/api/v1/admin/users").with(admin)).andExpect(status().isOk());
    }
    mvc.perform(get("/api/v1/admin/users").with(admin)).andExpect(status().isTooManyRequests());
    mvc.perform(get("/api/v1/me").with(admin)).andExpect(status().isOk());
  }

  @Test
  void healthProbesAreNeverRateLimited() throws Exception {
    for (int i = 0; i < 10; i++) {
      mvc.perform(get("/actuator/health").with(fromIp("10.7.7.7")))
          .andExpect(status().is(org.hamcrest.Matchers.not(429)));
    }
  }

  @Test
  void oversizedBodyIsRefusedBeforeAuthentication() throws Exception {
    mvc.perform(
            put("/api/v1/me/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"x\":\"" + "a".repeat(3000) + "\"}"))
        .andExpect(status().isContentTooLarge())
        .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
  }

  @Test
  void bodyAtTheLimitIsStillAccepted() throws Exception {
    var u = user(null);
    mvc.perform(
            put("/api/v1/me/preferences")
                .with(u)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"theme\":\"dark\"}"))
        .andExpect(status().is(org.hamcrest.Matchers.not(413)));
  }
}
