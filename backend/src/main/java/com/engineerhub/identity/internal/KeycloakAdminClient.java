package com.engineerhub.identity.internal;

import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Keycloak Admin REST API over a service-account token (least privilege: view-users +
 * manage-users). Timeouts are short, the token is cached until shortly before expiry, and nothing
 * from Keycloak's responses is ever echoed to API callers.
 */
@Component
@EnableConfigurationProperties(KeycloakAdminProperties.class)
class KeycloakAdminClient implements IdentityProviderAdmin {

  private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);
  private static final Duration SKEW = Duration.ofSeconds(30);

  record RoleRep(String id, String name) {}

  private final KeycloakAdminProperties props;
  private final Clock clock;
  private final RestClient http;
  private final String tokenUrl;
  private final String adminBase;
  private String token;
  private Instant tokenExpiry = Instant.EPOCH;

  @org.springframework.beans.factory.annotation.Autowired
  KeycloakAdminClient(
      KeycloakAdminProperties props,
      Clock clock,
      @Value("${engineerhub.security.jwt.issuer-uri}") String issuerUri) {
    this(props, clock, withTimeouts(RestClient.builder()), issuerUri);
  }

  /** For tests: a pre-built (e.g. mock-bound) client. */
  KeycloakAdminClient(
      KeycloakAdminProperties props, Clock clock, RestClient http, String issuerUri) {
    this.props = props;
    this.clock = clock;
    this.http = http;
    this.tokenUrl = issuerUri + "/protocol/openid-connect/token";
    this.adminBase = issuerUri.replace("/realms/", "/admin/realms/");
  }

  private static RestClient withTimeouts(RestClient.Builder builder) {
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(Duration.ofSeconds(5));
    return builder.requestFactory(factory).build();
  }

  @Override
  public boolean configured() {
    return props.configured();
  }

  @Override
  public Set<String> realmRolesOf(String userId) {
    List<RoleRep> roles =
        call(
            () ->
                http.get()
                    .uri(adminBase + "/users/{id}/role-mappings/realm", userId)
                    .headers(h -> h.setBearerAuth(accessToken()))
                    .retrieve()
                    .body(
                        new org.springframework.core.ParameterizedTypeReference<
                            List<RoleRep>>() {}));
    Set<String> names = new HashSet<>();
    if (roles != null) {
      roles.forEach(r -> names.add(r.name()));
    }
    return names;
  }

  @Override
  public void changeRealmRoles(String userId, Set<String> add, Set<String> remove) {
    if (!add.isEmpty()) {
      List<RoleRep> available =
          call(
              () ->
                  http.get()
                      .uri(adminBase + "/users/{id}/role-mappings/realm/available", userId)
                      .headers(h -> h.setBearerAuth(accessToken()))
                      .retrieve()
                      .body(
                          new org.springframework.core.ParameterizedTypeReference<
                              List<RoleRep>>() {}));
      List<RoleRep> reps =
          available == null
              ? List.of()
              : available.stream().filter(r -> add.contains(r.name())).toList();
      if (reps.size() != add.size()) {
        // A role that is unknown to the realm (or already assigned) must not be silently skipped.
        throw new ApiException(
            ErrorCode.CONFLICT, "A requested role is not available in the identity provider.");
      }
      call(
          () ->
              http.post()
                  .uri(adminBase + "/users/{id}/role-mappings/realm", userId)
                  .headers(h -> h.setBearerAuth(accessToken()))
                  .contentType(MediaType.APPLICATION_JSON)
                  .body(reps)
                  .retrieve()
                  .toBodilessEntity());
    }
    if (!remove.isEmpty()) {
      List<RoleRep> current =
          call(
              () ->
                  http.get()
                      .uri(adminBase + "/users/{id}/role-mappings/realm", userId)
                      .headers(h -> h.setBearerAuth(accessToken()))
                      .retrieve()
                      .body(
                          new org.springframework.core.ParameterizedTypeReference<
                              List<RoleRep>>() {}));
      List<RoleRep> reps =
          current == null
              ? List.of()
              : current.stream().filter(r -> remove.contains(r.name())).toList();
      if (!reps.isEmpty()) {
        call(
            () ->
                http.method(HttpMethod.DELETE)
                    .uri(adminBase + "/users/{id}/role-mappings/realm", userId)
                    .headers(h -> h.setBearerAuth(accessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(reps)
                    .retrieve()
                    .toBodilessEntity());
      }
    }
  }

  @Override
  public int countHolders(String role) {
    List<Map<String, Object>> holders =
        call(
            () ->
                http.get()
                    .uri(adminBase + "/roles/{role}/users?max=2", role)
                    .headers(h -> h.setBearerAuth(accessToken()))
                    .retrieve()
                    .body(
                        new org.springframework.core.ParameterizedTypeReference<
                            List<Map<String, Object>>>() {}));
    return holders == null ? 0 : holders.size();
  }

  // ---------------------------------------------------------------- plumbing

  private <T> T call(Supplier<T> request) {
    try {
      return request.get();
    } catch (HttpClientErrorException.Unauthorized e) {
      invalidateToken(); // revoked or rotated: one retry with a fresh token
      try {
        return request.get();
      } catch (RestClientException again) {
        throw unavailable(again);
      }
    } catch (HttpClientErrorException.NotFound e) {
      throw new ApiException(
          ErrorCode.CONFLICT, "The account does not exist in the identity provider.");
    } catch (RestClientException e) {
      throw unavailable(e);
    }
  }

  private static ApiException unavailable(Exception e) {
    // Log the class only: response bodies and URLs may carry identifiers we do not want in logs.
    log.warn("Identity provider admin call failed: {}", e.getClass().getSimpleName());
    return new ApiException(
        ErrorCode.SERVICE_UNAVAILABLE, "The identity provider is temporarily unavailable.", e);
  }

  private synchronized void invalidateToken() {
    token = null;
    tokenExpiry = Instant.EPOCH;
  }

  private synchronized String accessToken() {
    if (token != null && clock.instant().isBefore(tokenExpiry.minus(SKEW))) {
      return token;
    }
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", props.clientId());
    form.add("client_secret", props.clientSecret());
    Map<?, ?> response =
        http.post()
            .uri(tokenUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Map.class);
    if (response == null || !(response.get("access_token") instanceof String t)) {
      throw new RestClientException("token response without access_token");
    }
    long ttl = response.get("expires_in") instanceof Number n ? n.longValue() : 60;
    token = t;
    tokenExpiry = clock.instant().plusSeconds(ttl);
    return token;
  }
}
