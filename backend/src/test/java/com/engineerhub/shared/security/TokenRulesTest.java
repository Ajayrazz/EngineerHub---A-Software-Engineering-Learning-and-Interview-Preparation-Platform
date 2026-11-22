package com.engineerhub.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Pure unit tests of the token-acceptance rules (no Spring context, no Keycloak). */
class TokenRulesTest {

  private static Jwt jwt(Map<String, Object> claims) {
    Jwt.Builder b =
        Jwt.withTokenValue("t")
            .header("alg", "RS256")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60));
    claims.forEach(b::claim);
    if (!claims.containsKey("sub")) {
      b.subject("user-1");
    }
    return b.build();
  }

  // --- audience ---

  @Test
  void acceptsTokenIntendedForThisApi() {
    var validator = new AudienceValidator("engineerhub-api");
    assertThat(
            validator
                .validate(jwt(Map.of("aud", List.of("account", "engineerhub-api"))))
                .hasErrors())
        .isFalse();
  }

  @Test
  void rejectsTokenForAnotherAudience() {
    var validator = new AudienceValidator("engineerhub-api");
    assertThat(validator.validate(jwt(Map.of("aud", List.of("account")))).hasErrors()).isTrue();
  }

  @Test
  void rejectsTokenWithoutAudience() {
    var validator = new AudienceValidator("engineerhub-api");
    assertThat(validator.validate(jwt(Map.of())).hasErrors()).isTrue();
  }

  // --- roles ---

  private static List<String> authorities(Map<String, Object> claims) {
    return new RealmRoleConverter()
        .convert(jwt(claims)).stream().map(GrantedAuthority::getAuthority).sorted().toList();
  }

  @Test
  void mapsStaffRealmRolesToAuthorities() {
    var claims =
        Map.<String, Object>of("realm_access", Map.of("roles", List.of("ADMIN", "MODERATOR")));
    assertThat(authorities(claims)).containsExactly("ROLE_ADMIN", "ROLE_MODERATOR");
  }

  @Test
  void ignoresKeycloakBuiltInsAndUnknownRoles() {
    var claims =
        Map.<String, Object>of(
            "realm_access",
            Map.of(
                "roles",
                List.of(
                    "offline_access",
                    "uma_authorization",
                    "default-roles-engineerhub",
                    "PREMIUM_USER", // premium is an entitlement, never a role (ADR-006)
                    "FREE_USER",
                    "ROLE_ADMIN", // already-prefixed spoof attempt
                    "admin",
                    "INSTRUCTOR")));
    assertThat(authorities(claims)).containsExactly("ROLE_INSTRUCTOR");
  }

  @Test
  void toleratesMissingOrMalformedRoleClaims() {
    assertThat(authorities(Map.of())).isEmpty();
    assertThat(authorities(Map.of("realm_access", "nonsense"))).isEmpty();
    assertThat(authorities(Map.of("realm_access", Map.of("roles", "ADMIN")))).isEmpty();
    assertThat(authorities(Map.of("realm_access", Map.of("roles", List.of(1, 2))))).isEmpty();
  }

  // --- CORS ---

  @Test
  void corsIsEmptyWhenNoOriginsAreConfigured() {
    var source = CorsSupport.build(List.of());
    var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/x");
    request.addHeader("Origin", "https://evil.example");
    assertThat(source.getCorsConfiguration(request)).isNull();
  }

  @Test
  void corsAllowsOnlyListedOrigins() {
    var source = CorsSupport.build(List.of("https://app.example"));
    var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/x");
    var cfg = source.getCorsConfiguration(request);
    assertThat(cfg).isNotNull();
    assertThat(cfg.checkOrigin("https://app.example")).isEqualTo("https://app.example");
    assertThat(cfg.checkOrigin("https://evil.example")).isNull();
    assertThat(cfg.getAllowCredentials()).isFalse();
  }

  @Test
  void corsRefusesWildcardOrigin() {
    assertThatThrownBy(() -> CorsSupport.build(List.of("*")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("never '*'");
  }
}
