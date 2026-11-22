package com.engineerhub.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engineerhub.identity.UserDirectory;
import com.engineerhub.shared.id.Ids;
import com.engineerhub.testsupport.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * Just-in-time provisioning, the /me API, validation and suspension, against real PostgreSQL +
 * RabbitMQ.
 */
@IntegrationTest
class MeIT {

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired ObjectMapper mapper;
  @Autowired UserStatusCache statusCache;
  @Autowired UserDirectory directory;

  private static String newSub() {
    return "kc-" + Ids.newId();
  }

  private static JwtRequestPostProcessor user(String sub, String username, String email) {
    return jwt()
        .jwt(
            j ->
                j.subject(sub)
                    .claim("preferred_username", username)
                    .claim("email", email)
                    .claim("email_verified", true));
  }

  private String json(Object o) {
    return mapper.writeValueAsString(o);
  }

  private UUID me(JwtRequestPostProcessor u) throws Exception {
    MvcResult r = mvc.perform(get("/api/v1/me").with(u)).andExpect(status().isOk()).andReturn();
    return UUID.fromString(
        mapper.readTree(r.getResponse().getContentAsString()).get("id").asString());
  }

  private long registeredEvents(UUID userId) {
    return jdbc.sql(
            "select count(*) from shared.outbox where event_type = 'identity.user.registered' and payload->>'userId' = :id")
        .param("id", userId.toString())
        .query(Long.class)
        .single();
  }

  // ------------------------------------------------------------------ provisioning

  @Test
  void firstAuthenticatedCallProvisionsTheUserFromTheTokenClaims() throws Exception {
    String sub = newSub();
    mvc.perform(get("/api/v1/me").with(user(sub, "ada", "ada@example.test")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("ada"))
        .andExpect(jsonPath("$.email").value("ada@example.test"))
        .andExpect(jsonPath("$.emailVerified").value(true))
        .andExpect(jsonPath("$.status").value("active"))
        .andExpect(jsonPath("$.timezone").value("UTC"))
        .andExpect(jsonPath("$.profile.displayName").doesNotExist());

    assertThat(
            jdbc.sql("select count(*) from identity.users where keycloak_sub = :s")
                .param("s", sub)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql(
                    "select count(*) from identity.user_profiles p join identity.users u on u.id = p.user_id where u.keycloak_sub = :s")
                .param("s", sub)
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void idsAreTimeOrderedUuidV7() throws Exception {
    UUID id = me(user(newSub(), "grace", "grace@example.test"));
    assertThat(id.version()).isEqualTo(7);
  }

  @Test
  void repeatedCallsAreIdempotentAndEmitExactlyOneRegistrationEvent() throws Exception {
    String sub = newSub();
    UUID first = me(user(sub, "linus", "linus@example.test"));
    UUID second = me(user(sub, "linus", "linus@example.test"));
    assertThat(second).isEqualTo(first);
    assertThat(registeredEvents(first)).isEqualTo(1);
  }

  @Test
  void theRegistrationEventCarriesNoPersonalData() throws Exception {
    UUID id = me(user(newSub(), "margaret", "margaret.secret@example.test"));
    String payload =
        jdbc.sql(
                "select payload::text from shared.outbox where event_type = 'identity.user.registered' and payload->>'userId' = :id")
            .param("id", id.toString())
            .query(String.class)
            .single();
    assertThat(payload)
        .contains("margaret")
        .doesNotContain("example.test")
        .doesNotContain("secret");
  }

  @Test
  void tenParallelFirstRequestsCreateExactlyOneUserAndOneEvent() throws Exception {
    String sub = newSub();
    ExecutorService pool = Executors.newFixedThreadPool(10);
    try {
      List<Future<UUID>> calls = new ArrayList<>();
      for (int i = 0; i < 10; i++) {
        calls.add(pool.submit(() -> me(user(sub, "racer", "racer@example.test"))));
      }
      Set<UUID> ids = new java.util.HashSet<>();
      for (Future<UUID> f : calls) {
        ids.add(f.get());
      }
      assertThat(ids).hasSize(1);
      assertThat(
              jdbc.sql("select count(*) from identity.users where keycloak_sub = :s")
                  .param("s", sub)
                  .query(Long.class)
                  .single())
          .isEqualTo(1);
      assertThat(registeredEvents(ids.iterator().next())).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void claimChangesAreSyncedButUnchangedCallsDoNotRewriteTheRow() throws Exception {
    String sub = newSub();
    UUID id = me(user(sub, "dennis", "old@example.test"));
    var before =
        jdbc.sql("select updated_at, version from identity.users where id = :id")
            .param("id", id)
            .query((rs, i) -> rs.getTimestamp(1) + "|" + rs.getLong(2))
            .single();

    me(user(sub, "dennis", "old@example.test")); // nothing changed
    var unchanged =
        jdbc.sql("select updated_at, version from identity.users where id = :id")
            .param("id", id)
            .query((rs, i) -> rs.getTimestamp(1) + "|" + rs.getLong(2))
            .single();
    assertThat(unchanged).isEqualTo(before);

    mvc.perform(get("/api/v1/me").with(user(sub, "dennis", "new@example.test")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("new@example.test"));
  }

  @Test
  void unauthenticatedCallsAreRejected() throws Exception {
    mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    mvc.perform(patch("/api/v1/me/profile").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(put("/api/v1/me/preferences").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rolesAreReportedFromTheToken() throws Exception {
    mvc.perform(
            get("/api/v1/me")
                .with(
                    user(newSub(), "root", "root@example.test")
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_ADMIN"),
                            new SimpleGrantedAuthority("ROLE_MODERATOR"))))
        .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
        .andExpect(jsonPath("$.roles[1]").value("MODERATOR"));
  }

  // ------------------------------------------------------------------ profile

  @Test
  void profileUpdatesAreSelectiveAndClearable() throws Exception {
    JwtRequestPostProcessor u = user(newSub(), "ken", "ken@example.test");
    mvc.perform(
            patch("/api/v1/me/profile")
                .with(u)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        java.util.Map.of(
                            "displayName",
                            "Ken T.",
                            "bio",
                            "Line one\nLine two",
                            "website",
                            "https://ken.example/home",
                            "githubUrl",
                            "https://github.com/ken",
                            "linkedinUrl",
                            "https://www.linkedin.com/in/ken"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profile.displayName").value("Ken T."))
        .andExpect(jsonPath("$.profile.bio").value("Line one\nLine two"))
        .andExpect(jsonPath("$.profile.githubUrl").value("https://github.com/ken"));

    mvc.perform(
            patch("/api/v1/me/profile")
                .with(u)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bio\":\"\"}"))
        .andExpect(jsonPath("$.profile.bio").doesNotExist()) // cleared
        .andExpect(jsonPath("$.profile.displayName").value("Ken T.")); // untouched (field absent)
  }

  @Test
  void hostileOrMalformedProfileValuesAreRejectedWithoutEchoingThem() throws Exception {
    JwtRequestPostProcessor u = user(newSub(), "mallory", "m@example.test");
    String[] badBodies = {
      "{\"website\":\"javascript:alert(1)\"}",
      "{\"website\":\"data:text/html,<script>alert(1)</script>\"}",
      "{\"website\":\"http://insecure.example\"}",
      "{\"website\":\"https://user:pass@evil.example\"}",
      "{\"website\":\"https://evil.example:8443\"}",
      "{\"website\":\"https://exa mple.com\"}",
      "{\"website\":\"//evil.example\"}",
      "{\"githubUrl\":\"https://evil.example/ken\"}",
      "{\"githubUrl\":\"https://github.com.evil.example/ken\"}",
      "{\"linkedinUrl\":\"https://github.com/ken\"}",
      "{\"displayName\":\"bad\\u0000name\"}",
      "{\"displayName\":\"line\\nbreak\"}",
      "{\"bio\":\"x\\u001b[31m\"}",
      "{\"displayName\":\"" + "x".repeat(101) + "\"}",
      "{\"bio\":\"" + "x".repeat(501) + "\"}",
      "{\"website\":\"https://example.com/" + "a".repeat(300) + "\"}",
    };
    for (String body : badBodies) {
      MvcResult r =
          mvc.perform(
                  patch("/api/v1/me/profile")
                      .with(u)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(body))
              .andExpect(status().isBadRequest())
              .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
              .andReturn();
      assertThat(r.getResponse().getContentAsString())
          .as(body)
          .doesNotContain("alert(1)")
          .doesNotContain("evil.example");
    }
  }

  @Test
  void massAssignmentIsImpossible() throws Exception {
    String sub = newSub();
    JwtRequestPostProcessor u = user(sub, "eve", "eve@example.test");
    UUID id = me(u);
    mvc.perform(
            patch("/api/v1/me/profile")
                .with(u)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"displayName\":\"Eve\",\"status\":\"suspended\",\"id\":\""
                        + UUID.randomUUID()
                        + "\",\"email\":\"evil@x.test\",\"roles\":[\"SUPER_ADMIN\"],\"keycloakSub\":\"someone-else\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.status").value("active"))
        .andExpect(jsonPath("$.email").value("eve@example.test"))
        .andExpect(jsonPath("$.roles.length()").value(0));
    assertThat(
            jdbc.sql("select keycloak_sub from identity.users where id = :id")
                .param("id", id)
                .query(String.class)
                .single())
        .isEqualTo(sub);
  }

  @Test
  void usersCanOnlyEverChangeTheirOwnProfile() throws Exception {
    JwtRequestPostProcessor a = user(newSub(), "alice", "a@example.test");
    JwtRequestPostProcessor b = user(newSub(), "bob", "b@example.test");
    me(b);
    mvc.perform(
            patch("/api/v1/me/profile")
                .with(a)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Alice\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/me").with(b))
        .andExpect(jsonPath("$.profile.displayName").doesNotExist());
  }

  // ------------------------------------------------------------------ preferences

  @Test
  void preferencesValidateTheTimezoneAndLocale() throws Exception {
    JwtRequestPostProcessor u = user(newSub(), "tz", "tz@example.test");
    mvc.perform(
            put("/api/v1/me/preferences")
                .with(u)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"timezone\":\"Asia/Kolkata\",\"locale\":\"en\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"));
    for (String bad :
        new String[] {
          "{\"timezone\":\"Mars/Olympus\",\"locale\":\"en\"}",
          "{\"timezone\":\"\",\"locale\":\"en\"}",
          "{\"timezone\":\"UTC\",\"locale\":\"fr\"}",
          "{\"locale\":\"en\"}",
          "{\"timezone\":\"../../etc\",\"locale\":\"en\"}"
        }) {
      mvc.perform(
              put("/api/v1/me/preferences")
                  .with(u)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(bad))
          .andExpect(status().isBadRequest());
    }
  }

  // ------------------------------------------------------------------ suspension

  @Test
  void aSuspendedAccountIsBlockedEverywhereAndRestoredWhenReactivated() throws Exception {
    String sub = newSub();
    JwtRequestPostProcessor u = user(sub, "banned", "banned@example.test");
    UUID id = me(u);

    jdbc.sql("update identity.users set status = 'suspended' where id = :id")
        .param("id", id)
        .update();
    statusCache.evict(sub);

    mvc.perform(get("/api/v1/me").with(u))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    mvc.perform(get("/api/v1/system/whoami").with(u))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    mvc.perform(
            patch("/api/v1/me/profile")
                .with(u)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"x\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/system/ping"))
        .andExpect(status().isOk()); // public endpoints without a token are unaffected

    jdbc.sql("update identity.users set status = 'active' where id = :id").param("id", id).update();
    statusCache.evict(sub);
    mvc.perform(get("/api/v1/me").with(u)).andExpect(status().isOk());
  }

  @Test
  void deletedAndPendingDeletionAccountsAreBlockedToo() throws Exception {
    for (String blocked : new String[] {"deleted", "pending_deletion"}) {
      String sub = newSub();
      JwtRequestPostProcessor u = user(sub, "gone-" + blocked.substring(0, 3), "g@example.test");
      UUID id = me(u);
      jdbc.sql("update identity.users set status = :s where id = :id")
          .param("s", blocked)
          .param("id", id)
          .update();
      statusCache.evict(sub);
      mvc.perform(get("/api/v1/me").with(u)).andExpect(status().isForbidden());
    }
  }

  // ------------------------------------------------------------------ public API for other modules

  @Test
  void theUserDirectoryProvidesAStableViewForOtherModules() throws Exception {
    String sub = newSub();
    var auth =
        (org.springframework.security.core.Authentication)
            new org.springframework.security.oauth2.server.resource.authentication
                .JwtAuthenticationToken(
                org.springframework.security.oauth2.jwt.Jwt.withTokenValue("t")
                    .header("alg", "none")
                    .subject(sub)
                    .claim("preferred_username", "dir")
                    .build());
    var ref =
        new org.springframework.transaction.support.TransactionTemplate(txm)
            .execute(s -> directory.current(auth));
    assertThat(ref.active()).isTrue();
    assertThat(directory.findBySubject(sub)).contains(ref);
    assertThat(directory.findById(ref.id())).contains(ref);
    assertThat(directory.findBySubject("nobody")).isEmpty();
    assertThat(Set.of(ref.id()).stream().map(UUID::version).collect(Collectors.toSet()))
        .containsExactly(7);
  }

  @Autowired org.springframework.transaction.PlatformTransactionManager txm;
}
