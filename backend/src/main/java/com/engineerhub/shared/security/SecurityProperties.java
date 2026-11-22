package com.engineerhub.shared.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code engineerhub.security.*} settings. Fail-fast: the app does not start without an issuer.
 *
 * @param jwt token validation settings
 * @param cors browser cross-origin allowlist (empty = no CORS headers at all; the Next.js BFF calls
 *     the API server-side, so this normally stays empty)
 * @param contentSecurityPolicy CSP header for API responses (JSON only → locked down; the local
 *     profile relaxes it so Swagger UI works)
 */
@Validated
@ConfigurationProperties("engineerhub.security")
record SecurityProperties(@Valid Jwt jwt, Cors cors, @NotBlank String contentSecurityPolicy) {

  record Jwt(@NotBlank String issuerUri, @NotBlank String audience) {}

  record Cors(List<String> allowedOrigins) {
    Cors {
      allowedOrigins =
          allowedOrigins == null
              ? List.of()
              : allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
  }
}
