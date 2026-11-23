package com.engineerhub.shared.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Stateless OAuth2 resource server (Keycloak-issued JWTs).
 *
 * <p><b>Defence in depth:</b> anonymous state-changing requests are rejected here (401) before
 * Spring binds or validates the body — otherwise a protected endpoint would answer an anonymous
 * caller with validation errors (400) instead of 401. Public mutations (webhooks...) must be listed
 * explicitly in {@code engineerhub.security.public-mutation-paths}.
 *
 * <p><b>Authorization model:</b> method security ({@code @PreAuthorize} / {@code @PermitAll}) is
 * the single enforcement point and every handler method must carry one of them — verified at build
 * time by {@code EndpointPolicyArchTest}. The URL layer is deliberately permissive so public
 * endpoints (course catalogue, health) need no duplicate allowlist here; an unannotated endpoint is
 * therefore a build failure, not a silent hole. Unauthenticated calls to protected methods yield
 * 401, wrong role 403.
 *
 * <p><b>CSRF is disabled</b> because the API accepts only {@code Authorization: Bearer} tokens,
 * sets no cookies and keeps no session; the browser-facing CSRF defences live in the Next.js BFF
 * (docs/08).
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfig {

  @Bean
  SecurityFilterChain apiSecurity(
      HttpSecurity http,
      ProblemSecurityHandlers problemHandlers,
      JwtAuthenticationConverter jwtAuthenticationConverter,
      SecurityProperties props,
      java.util.List<AuthenticatedRequestGuard> guards,
      com.engineerhub.shared.limits.RateLimitFilter rateLimitFilter)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(Customizer.withDefaults())
        .headers(
            h ->
                h.contentSecurityPolicy(c -> c.policyDirectives(props.contentSecurityPolicy()))
                    .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
                    .frameOptions(f -> f.deny())
                    .permissionsPolicyHeader(
                        p -> p.policy("geolocation=(), microphone=(), camera=()")))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(new AnonymousMutationMatcher(props.publicMutationPaths()))
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .addFilterAfter(
            new AuthenticatedRequestGuardFilter(guards, problemHandlers),
            org.springframework.security.oauth2.server.resource.web.authentication
                .BearerTokenAuthenticationFilter.class)
        .addFilterAfter(rateLimitFilter, AuthenticatedRequestGuardFilter.class)
        .exceptionHandling(
            e -> e.authenticationEntryPoint(problemHandlers).accessDeniedHandler(problemHandlers))
        .oauth2ResourceServer(
            o ->
                o.jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter))
                    .authenticationEntryPoint(problemHandlers)
                    .accessDeniedHandler(problemHandlers));
    return http.build();
  }

  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(new RealmRoleConverter());
    return converter;
  }

  /**
   * Validates signature (JWKS discovered from the issuer), expiry, issuer and audience. Built
   * lazily so the API can start while Keycloak is still booting; discovery is retried on the next
   * request.
   */
  @Bean
  JwtDecoder jwtDecoder(SecurityProperties props) {
    String issuer = props.jwt().issuerUri();
    OAuth2TokenValidator<Jwt> validator =
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer),
            new AudienceValidator(props.jwt().audience()));
    return new ResilientJwtDecoder(
        new SupplierJwtDecoder(
            () -> {
              NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
              decoder.setJwtValidator(validator);
              return decoder;
            }));
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(SecurityProperties props) {
    return CorsSupport.build(props.cors().allowedOrigins());
  }
}
