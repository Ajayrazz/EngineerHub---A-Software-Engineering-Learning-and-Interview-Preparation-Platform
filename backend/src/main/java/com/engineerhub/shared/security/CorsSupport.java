package com.engineerhub.shared.security;

import java.util.List;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Builds the CORS policy: an exact-origin allowlist, or no CORS at all when the list is empty. */
final class CorsSupport {

  private CorsSupport() {}

  static CorsConfigurationSource build(List<String> origins) {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    if (origins.isEmpty()) {
      return source; // no registrations => no CORS headers are ever emitted
    }
    if (origins.contains("*")) {
      throw new IllegalStateException(
          "engineerhub.security.cors.allowed-origins must list explicit origins, never '*'");
    }
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(origins);
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
    cors.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-Id"));
    cors.setExposedHeaders(List.of("X-Correlation-Id"));
    cors.setAllowCredentials(false);
    cors.setMaxAge(3600L);
    source.registerCorsConfiguration("/**", cors);
    return source;
  }
}
