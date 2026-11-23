package com.engineerhub.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * The HTTP contract with Keycloak's admin API (mock server; the live check is
 * scripts/verify-api-auth.sh).
 */
class KeycloakAdminClientTest {

  private static final String ISSUER = "http://kc.test/realms/engineerhub";
  private static final String ADMIN = "http://kc.test/admin/realms/engineerhub";
  private static final String TOKEN_URL = ISSUER + "/protocol/openid-connect/token";
  private static final String TOKEN_JSON = "{\"access_token\":\"tok-1\",\"expires_in\":300}";

  private MockRestServiceServer server;
  private KeycloakAdminClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    client =
        new KeycloakAdminClient(
            new KeycloakAdminProperties("svc", "s3cret-value"),
            Clock.fixed(Instant.parse("2026-11-23T00:00:00Z"), ZoneOffset.UTC),
            builder.build(),
            ISSUER);
  }

  private void expectToken() {
    server
        .expect(ExpectedCount.once(), requestTo(TOKEN_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(
            content()
                .formDataContains(
                    java.util.Map.of(
                        "grant_type",
                        "client_credentials",
                        "client_id",
                        "svc",
                        "client_secret",
                        "s3cret-value")))
        .andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
  }

  @Test
  void configurationIsDetectedFromTheCredentials() {
    assertThat(client.configured()).isTrue();
    assertThat(new KeycloakAdminProperties("", "x").configured()).isFalse();
    assertThat(new KeycloakAdminProperties("svc", null).configured()).isFalse();
  }

  @Test
  void readsRolesWithABearerTokenAndCachesTheToken() {
    expectToken(); // exactly once for two calls
    server
        .expect(ExpectedCount.twice(), requestTo(ADMIN + "/users/u-1/role-mappings/realm"))
        .andExpect(header("Authorization", "Bearer tok-1"))
        .andRespond(
            withSuccess(
                "[{\"id\":\"r1\",\"name\":\"ADMIN\"},{\"id\":\"r2\",\"name\":\"STAFF_MFA\"}]",
                MediaType.APPLICATION_JSON));

    assertThat(client.realmRolesOf("u-1")).containsExactlyInAnyOrder("ADMIN", "STAFF_MFA");
    assertThat(client.realmRolesOf("u-1")).hasSize(2);
    server.verify();
  }

  @Test
  void addsRolesUsingTheIdsKeycloakReports() {
    expectToken();
    server
        .expect(requestTo(ADMIN + "/users/u-1/role-mappings/realm/available"))
        .andRespond(
            withSuccess(
                "[{\"id\":\"id-mod\",\"name\":\"MODERATOR\"},{\"id\":\"id-inst\",\"name\":\"INSTRUCTOR\"}]",
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(ADMIN + "/users/u-1/role-mappings/realm"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("[{\"id\":\"id-mod\",\"name\":\"MODERATOR\"}]"))
        .andRespond(withNoContent());

    client.changeRealmRoles("u-1", Set.of("MODERATOR"), Set.of());
    server.verify();
  }

  @Test
  void aRoleKeycloakDoesNotOfferIsAnErrorNotASilentSkip() {
    expectToken();
    server
        .expect(requestTo(ADMIN + "/users/u-1/role-mappings/realm/available"))
        .andRespond(
            withSuccess(
                "[{\"id\":\"id-mod\",\"name\":\"MODERATOR\"}]", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.changeRealmRoles("u-1", Set.of("MODERATOR", "GHOST"), Set.of()))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT));
  }

  @Test
  void removesRolesWithADeleteCarryingTheRepresentations() {
    expectToken();
    server
        .expect(requestTo(ADMIN + "/users/u-1/role-mappings/realm"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "[{\"id\":\"id-mod\",\"name\":\"MODERATOR\"},{\"id\":\"id-adm\",\"name\":\"ADMIN\"}]",
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(ADMIN + "/users/u-1/role-mappings/realm"))
        .andExpect(method(HttpMethod.DELETE))
        .andExpect(content().json("[{\"id\":\"id-mod\",\"name\":\"MODERATOR\"}]"))
        .andRespond(withNoContent());

    client.changeRealmRoles("u-1", Set.of(), Set.of("MODERATOR"));
    server.verify();
  }

  @Test
  void countsHoldersOfARole() {
    expectToken();
    server
        .expect(requestTo(ADMIN + "/roles/SUPER_ADMIN/users?max=2"))
        .andRespond(withSuccess("[{\"id\":\"a\"}]", MediaType.APPLICATION_JSON));
    assertThat(client.countHolders("SUPER_ADMIN")).isEqualTo(1);
  }

  @Test
  void aRejectedTokenIsRefreshedOnceAndTheCallRetried() {
    server
        .expect(requestTo(TOKEN_URL))
        .andRespond(
            withSuccess(
                "{\"access_token\":\"stale\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(ADMIN + "/users/u-1/role-mappings/realm"))
        .andExpect(header("Authorization", "Bearer stale"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
    server
        .expect(requestTo(TOKEN_URL))
        .andRespond(
            withSuccess(
                "{\"access_token\":\"fresh\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(ADMIN + "/users/u-1/role-mappings/realm"))
        .andExpect(header("Authorization", "Bearer fresh"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    assertThat(client.realmRolesOf("u-1")).isEmpty();
    server.verify();
  }

  @Test
  void aServerErrorBecomesA503WithoutLeakingTheUpstreamBody() {
    expectToken();
    server
        .expect(requestTo(ADMIN + "/users/u-1/role-mappings/realm"))
        .andRespond(
            withServerError()
                .body("internal: jdbc:postgresql://secret-host/kc password=hunter2")
                .contentType(MediaType.TEXT_PLAIN));

    assertThatThrownBy(() -> client.realmRolesOf("u-1"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
              assertThat(e.getMessage()).doesNotContain("hunter2", "secret-host");
            });
  }

  @Test
  void aUserMissingInKeycloakIsAConflictNotAServerError() {
    expectToken();
    server
        .expect(requestTo(ADMIN + "/users/u-1/role-mappings/realm"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));
    assertThatThrownBy(() -> client.realmRolesOf("u-1"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT));
  }
}
