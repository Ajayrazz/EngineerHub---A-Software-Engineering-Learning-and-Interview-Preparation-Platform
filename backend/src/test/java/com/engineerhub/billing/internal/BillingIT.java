package com.engineerhub.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engineerhub.shared.id.Ids;
import com.engineerhub.testsupport.IntegrationTest;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Plans, prices, access periods and the entitlement decision that gates premium content. */
@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BillingIT {

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired ObjectMapper mapper;
  @Autowired com.engineerhub.billing.EntitlementService entitlements;

  private static JwtRequestPostProcessor who(String sub, String... roles) {
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

  private final JwtRequestPostProcessor admin = who(sub(), "ADMIN");
  private final JwtRequestPostProcessor manager = who(sub(), "CONTENT_MANAGER");

  private ResultActions call(
      MockHttpServletRequestBuilder b, JwtRequestPostProcessor who, String body) throws Exception {
    MockHttpServletRequestBuilder req = who == null ? b : b.with(who);
    return mvc.perform(
        body == null ? req : req.contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private JsonNode json(ResultActions r) throws Exception {
    return mapper.readTree(r.andReturn().getResponse().getContentAsString());
  }

  // fixtures: one premium lesson and one premium problem
  private String courseSlug;
  private String lessonSlug = "deep";
  private String lessonId;
  private String problemSlug;

  @BeforeAll
  void fixture() throws Exception {
    String tag = Ids.newId().toString().substring(24);
    courseSlug = "bill-" + tag;
    String c =
        json(call(
                post("/api/v1/admin/courses"),
                manager,
                "{\"slug\":\"%s\",\"title\":\"B\",\"description\":\"d\",\"difficulty\":\"beginner\",\"accessTier\":\"premium\"}"
                    .formatted(courseSlug)))
            .get("id")
            .asString();
    String s =
        json(call(post("/api/v1/admin/courses/" + c + "/sections"), manager, "{\"title\":\"S\"}"))
            .get("sections")
            .get(0)
            .get("id")
            .asString();
    lessonId =
        json(call(
                post("/api/v1/admin/courses/" + c + "/lessons"),
                manager,
                "{\"sectionId\":\"%s\",\"slug\":\"%s\",\"title\":\"Deep\"}"
                    .formatted(s, lessonSlug)))
            .get("id")
            .asString();
    call(
            put("/api/v1/admin/courses/" + c + "/lessons/" + lessonId + "/blocks"),
            manager,
            "{\"expectedVersion\":0,\"blocks\":[{\"type\":\"markdown\",\"data\":{\"text\":\"PREMIUM-LESSON-BODY\"}}]}")
        .andExpect(status().isOk());
    call(
            put("/api/v1/admin/courses/" + c + "/lessons/" + lessonId + "/source"),
            manager,
            "{\"kind\":\"original\"}")
        .andExpect(status().isOk());
    call(
            post("/api/v1/admin/courses/" + c + "/lessons/" + lessonId + "/publish"),
            manager,
            "{\"originalityConfirmed\":true}")
        .andExpect(status().isOk());
    call(post("/api/v1/admin/courses/" + c + "/publish"), manager, null).andExpect(status().isOk());

    problemSlug = "bill-p-" + tag;
    String p =
        json(call(
                post("/api/v1/admin/problems"),
                admin,
                "{\"slug\":\"%s\",\"title\":\"BP\",\"difficulty\":\"easy\",\"accessTier\":\"premium\"}"
                    .formatted(problemSlug)))
            .get("id")
            .asString();
    int v = json(call(get("/api/v1/admin/problems/" + p), admin, null)).get("version").asInt();
    call(
            patch("/api/v1/admin/problems/" + p),
            admin,
            "{\"statement\":\"PREMIUM-PROBLEM-BODY\",\"inputFormat\":\"i\",\"outputFormat\":\"o\",\"expectedVersion\":%d}"
                .formatted(v))
        .andExpect(status().isOk());
    call(
            put("/api/v1/admin/problems/" + p + "/tests"),
            admin,
            "{\"tests\":[{\"input\":\"1\",\"expected\":\"1\",\"sample\":true},{\"input\":\"2\",\"expected\":\"2\",\"sample\":false}],\"expectedTestcaseVersion\":0}")
        .andExpect(status().isOk());
    call(put("/api/v1/admin/problems/" + p + "/source"), admin, "{\"kind\":\"original\"}")
        .andExpect(status().isOk());
    jdbc.sql("update problems.problems set validation_status = 'verified' where id = :id")
        .param("id", UUID.fromString(p))
        .update();
    call(post("/api/v1/admin/problems/" + p + "/publish"), admin, "{\"originalityConfirmed\":true}")
        .andExpect(status().isOk());
  }

  private String plan(boolean withEntitlement) throws Exception {
    String code = "plan-" + Ids.newId().toString().substring(24);
    String id =
        json(call(
                    post("/api/v1/admin/billing/plans"),
                    admin,
                    "{\"code\":\"%s\",\"name\":\"Plan %s\"}".formatted(code, code))
                .andExpect(status().isCreated()))
            .get("id")
            .asString();
    if (withEntitlement) {
      call(
              put("/api/v1/admin/billing/plans/" + id + "/entitlements"),
              admin,
              "{\"codes\":[\"premium_content\"]}")
          .andExpect(status().isOk());
    }
    return id;
  }

  private String grant(String userId, String planId, int days) throws Exception {
    return json(call(
                post("/api/v1/admin/billing/users/" + userId + "/access-grants"),
                admin,
                "{\"planId\":\"%s\",\"days\":%d,\"reason\":\"billing test grant\"}"
                    .formatted(planId, days))
            .andExpect(status().isCreated()))
        .get("periods")
        .get(0)
        .get("id")
        .asString();
  }

  private String userId(JwtRequestPostProcessor u) throws Exception {
    return json(call(get("/api/v1/me"), u, null)).get("id").asString();
  }

  private ResultActions lesson(JwtRequestPostProcessor u) throws Exception {
    return call(get("/api/v1/courses/" + courseSlug + "/lessons/" + lessonSlug), u, null);
  }

  private ResultActions problem(JwtRequestPostProcessor u) throws Exception {
    return call(get("/api/v1/problems/" + problemSlug), u, null);
  }

  private long audits(String action) {
    return jdbc.sql("select count(*) from admin.audit_logs where action = :a")
        .param("a", action)
        .query(Long.class)
        .single();
  }

  // ------------------------------------------------------------------ authorization

  @Test
  void everyAdminEndpointNeedsBillingManage() throws Exception {
    String planId = plan(true);
    UUID target = UUID.randomUUID();
    record Case(String name, MockHttpServletRequestBuilder req, String body) {}
    List<Case> cases =
        List.of(
            new Case("list plans", get("/api/v1/admin/billing/plans"), null),
            new Case(
                "create plan",
                post("/api/v1/admin/billing/plans"),
                "{\"code\":\"x-" + Ids.newId().toString().substring(24) + "\",\"name\":\"X\"}"),
            new Case(
                "patch plan", patch("/api/v1/admin/billing/plans/" + planId), "{\"name\":\"N\"}"),
            new Case(
                "entitlements",
                put("/api/v1/admin/billing/plans/" + planId + "/entitlements"),
                "{\"codes\":[]}"),
            new Case(
                "add price",
                post("/api/v1/admin/billing/plans/" + planId + "/prices"),
                "{\"billingMode\":\"ONE_TIME\",\"periodUnit\":\"month\",\"amountInclTaxMinor\":10000}"),
            new Case(
                "patch price",
                patch("/api/v1/admin/billing/plans/" + planId + "/prices/" + target),
                "{\"active\":false}"),
            new Case("user access", get("/api/v1/admin/billing/users/" + target + "/access"), null),
            new Case(
                "grant",
                post("/api/v1/admin/billing/users/" + target + "/access-grants"),
                "{\"planId\":\"" + planId + "\",\"days\":1,\"reason\":\"auth table test\"}"),
            new Case(
                "revoke",
                post("/api/v1/admin/billing/access-periods/" + target + "/revoke"),
                "{\"reason\":\"auth table test\"}"));
    for (Case k : cases) {
      call(k.req(), null, k.body()).andExpect(status().isUnauthorized());
      for (var d :
          List.of(
              who(sub()),
              who(sub(), "MODERATOR"),
              who(sub(), "CONTENT_MANAGER"),
              who(sub(), "INSTRUCTOR"),
              who(sub(), "PROBLEM_SETTER"))) {
        call(k.req(), d, k.body()).andExpect(status().isForbidden());
      }
      for (var staff : List.of(admin, who(sub(), "SUPER_ADMIN"))) {
        assertThat(call(k.req(), staff, k.body()).andReturn().getResponse().getStatus())
            .as("staff: " + k.name())
            .isNotIn(401, 403);
      }
    }
    call(get("/api/v1/me/access"), null, null).andExpect(status().isUnauthorized());
  }

  // ------------------------------------------------------------------ plans and prices

  @Test
  void plansAndPricesAreValidatedAndOnlyBuyablePlansAreAdvertised() throws Exception {
    String planId = plan(true);
    String planUrl = "/api/v1/admin/billing/plans/" + planId;
    call(post("/api/v1/admin/billing/plans"), admin, "{\"code\":\"Bad Code\",\"name\":\"x\"}")
        .andExpect(status().isBadRequest());
    String code =
        json(call(get(planUrl.replace("/plans/" + planId, "/plans")), admin, null))
            .get(0)
            .get("code")
            .asString();
    call(
            post("/api/v1/admin/billing/plans"),
            admin,
            "{\"code\":\"%s\",\"name\":\"dup\"}".formatted(code))
        .andExpect(status().isConflict());
    for (String bad :
        new String[] {
          "{\"billingMode\":\"ONE_TIME\",\"periodUnit\":\"month\",\"amountInclTaxMinor\":99}",
          "{\"billingMode\":\"ONE_TIME\",\"periodUnit\":\"month\",\"amountInclTaxMinor\":100000001}",
          "{\"billingMode\":\"LIFETIME\",\"periodUnit\":\"month\",\"amountInclTaxMinor\":10000}",
          "{\"billingMode\":\"ONE_TIME\",\"periodUnit\":\"week\",\"amountInclTaxMinor\":10000}"
        }) {
      call(post(planUrl + "/prices"), admin, bad).andExpect(status().isBadRequest());
    }
    call(put(planUrl + "/entitlements"), admin, "{\"codes\":[\"everything\"]}")
        .andExpect(status().isBadRequest());
    call(
            post("/api/v1/admin/billing/plans/" + UUID.randomUUID() + "/prices"),
            admin,
            "{\"billingMode\":\"ONE_TIME\",\"periodUnit\":\"month\",\"amountInclTaxMinor\":10000}")
        .andExpect(status().isNotFound());

    // a plan without an active price is not advertised
    assertThat(publicPlanIds()).doesNotContain(planId);
    String priceId =
        json(call(
                    post(planUrl + "/prices"),
                    admin,
                    "{\"billingMode\":\"RECURRING\",\"periodUnit\":\"year\",\"amountInclTaxMinor\":299900}")
                .andExpect(status().isCreated()))
            .get("prices")
            .get(0)
            .get("id")
            .asString();
    assertThat(publicPlanIds()).contains(planId);
    call(get("/api/v1/plans"), null, null)
        .andExpect(header().string("Cache-Control", Matchers.containsString("public")));
    JsonNode shown = null;
    for (JsonNode p : json(call(get("/api/v1/plans"), null, null))) {
      if (p.get("id").asString().equals(planId)) shown = p;
    }
    assertThat(shown.get("prices").get(0).get("amountInclTaxMinor").asLong()).isEqualTo(299_900);
    assertThat(shown.get("prices").get(0).get("currency").asString()).isEqualTo("INR");

    // prices are switched off, never edited
    call(patch(planUrl + "/prices/" + priceId), admin, "{\"active\":false}")
        .andExpect(status().isOk());
    assertThat(publicPlanIds()).doesNotContain(planId);
    call(patch(planUrl + "/prices/" + UUID.randomUUID()), admin, "{\"active\":false}")
        .andExpect(status().isNotFound());
    // an inactive plan is not advertised either
    call(
            post(planUrl + "/prices"),
            admin,
            "{\"billingMode\":\"ONE_TIME\",\"periodUnit\":\"month\",\"amountInclTaxMinor\":39900}")
        .andExpect(status().isCreated());
    assertThat(publicPlanIds()).contains(planId);
    call(patch(planUrl), admin, "{\"active\":false}").andExpect(status().isOk());
    assertThat(publicPlanIds()).doesNotContain(planId);
    assertThat(audits("billing.price.created")).isGreaterThanOrEqualTo(2);
  }

  private List<String> publicPlanIds() throws Exception {
    List<String> ids = new java.util.ArrayList<>();
    json(call(get("/api/v1/plans"), null, null)).forEach(p -> ids.add(p.get("id").asString()));
    return ids;
  }

  // ------------------------------------------------------------------ access and the entitlement
  // decision

  @Test
  void aGrantUnlocksPremiumContentEverywhereAndARevocationTakesEffectAtOnce() throws Exception {
    var learner = who(sub());
    var other = who(sub());
    String id = userId(learner);
    userId(other);

    lesson(learner).andExpect(status().isForbidden());
    problem(learner).andExpect(status().isForbidden());
    call(get("/api/v1/me/access"), learner, null).andExpect(jsonPath("$.premium").value(false));

    String period = grant(id, plan(true), 30);
    call(get("/api/v1/me/access"), learner, null)
        .andExpect(jsonPath("$.premium").value(true))
        .andExpect(jsonPath("$.validThrough").exists())
        .andExpect(jsonPath("$.periods[0].sourceType").value("admin_grant"));
    lesson(learner)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.blocks[0].data.text").value("PREMIUM-LESSON-BODY"))
        .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")));
    problem(learner)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statement").value("PREMIUM-PROBLEM-BODY"));
    call(put("/api/v1/lessons/" + lessonId + "/progress"), learner, "{\"status\":\"completed\"}")
        .andExpect(status().isOk()); // progress needs the same entitlement

    // someone else is unaffected
    lesson(other).andExpect(status().isForbidden());
    problem(other).andExpect(status().isForbidden());

    // revocation: no cache can keep the access alive
    call(
            post("/api/v1/admin/billing/access-periods/" + period + "/revoke"),
            admin,
            "{\"reason\":\"chargeback test\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.premium").value(false))
        .andExpect(jsonPath("$.periods[0].revoked").value(true));
    lesson(learner).andExpect(status().isForbidden());
    problem(learner).andExpect(status().isForbidden());
    call(put("/api/v1/lessons/" + lessonId + "/progress"), learner, "{\"status\":\"completed\"}")
        .andExpect(status().isForbidden());
    call(
            post("/api/v1/admin/billing/access-periods/" + period + "/revoke"),
            admin,
            "{\"reason\":\"again please\"}")
        .andExpect(status().isConflict());
    call(
            post("/api/v1/admin/billing/access-periods/" + UUID.randomUUID() + "/revoke"),
            admin,
            "{\"reason\":\"unknown period\"}")
        .andExpect(status().isNotFound());

    assertThat(audits("billing.access.granted")).isGreaterThanOrEqualTo(1);
    assertThat(audits("billing.access.revoked")).isGreaterThanOrEqualTo(1);
    assertThat(
            jdbc.sql(
                    "select count(*) from shared.outbox where event_type = 'billing.access.granted' and aggregate_id = :a")
                .param("a", id)
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void expiredAndNotYetStartedPeriodsDoNotEntitle() throws Exception {
    var learner = who(sub());
    UUID id = UUID.fromString(userId(learner));
    UUID plan = UUID.fromString(plan(true));
    jdbc.sql(
            "insert into billing.access_periods (id, user_id, plan_id, starts_at, ends_at, source_type, created_at) values (:i, :u, :p, now() - interval '10 days', now() - interval '1 second', 'admin_grant', now())")
        .param("i", Ids.newId())
        .param("u", id)
        .param("p", plan)
        .update();
    jdbc.sql(
            "insert into billing.access_periods (id, user_id, plan_id, starts_at, ends_at, source_type, created_at) values (:i, :u, :p, now() + interval '1 day', now() + interval '10 days', 'admin_grant', now())")
        .param("i", Ids.newId())
        .param("u", id)
        .param("p", plan)
        .update();
    lesson(learner).andExpect(status().isForbidden());
    call(get("/api/v1/me/access"), learner, null)
        .andExpect(jsonPath("$.premium").value(false))
        .andExpect(jsonPath("$.periods.length()").value(2));
    // a running one on top makes it work, and validThrough is its end
    jdbc.sql(
            "insert into billing.access_periods (id, user_id, plan_id, starts_at, ends_at, source_type, created_at) values (:i, :u, :p, now() - interval '1 day', now() + interval '2 days', 'admin_grant', now())")
        .param("i", Ids.newId())
        .param("u", id)
        .param("p", plan)
        .update();
    lesson(learner).andExpect(status().isOk());
  }

  @Test
  void entitlementsAreDerivedFromThePlanAtReadTime() throws Exception {
    var learner = who(sub());
    String id = userId(learner);
    String planId = plan(true);
    grant(id, planId, 30);
    lesson(learner).andExpect(status().isOk());
    // taking the entitlement off the plan removes access from every holder immediately
    call(put("/api/v1/admin/billing/plans/" + planId + "/entitlements"), admin, "{\"codes\":[]}")
        .andExpect(status().isOk());
    lesson(learner).andExpect(status().isForbidden());
    call(
            put("/api/v1/admin/billing/plans/" + planId + "/entitlements"),
            admin,
            "{\"codes\":[\"premium_content\"]}")
        .andExpect(status().isOk());
    lesson(learner).andExpect(status().isOk());
    // retiring a plan does not take back what people already have
    call(patch("/api/v1/admin/billing/plans/" + planId), admin, "{\"active\":false}")
        .andExpect(status().isOk());
    lesson(learner).andExpect(status().isOk());
  }

  @Test
  void aSuspendedAccountIsNotEntitledEvenIfTheApiGuardWereBypassed() throws Exception {
    String sub = sub();
    var learner = who(sub);
    String id = userId(learner);
    grant(id, plan(true), 30);
    var jwt =
        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("t")
            .header("alg", "none")
            .subject(sub)
            .build();
    var auth =
        new org.springframework.security.oauth2.server.resource.authentication
            .JwtAuthenticationToken(jwt);
    assertThat(entitlements.canReadPremium(auth, "course", UUID.randomUUID())).isTrue();
    call(
            put("/api/v1/admin/users/" + id + "/status"),
            who(sub(), "SUPER_ADMIN"),
            "{\"status\":\"suspended\",\"reason\":\"billing test\"}")
        .andExpect(status().isOk());
    assertThat(entitlements.canReadPremium(auth, "course", UUID.randomUUID())).isFalse();
    assertThat(entitlements.canReadPremium(null, "course", UUID.randomUUID())).isFalse();
    var unknown =
        new org.springframework.security.oauth2.server.resource.authentication
            .JwtAuthenticationToken(
            org.springframework.security.oauth2.jwt.Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("kc-never-seen")
                .build());
    assertThat(entitlements.canReadPremium(unknown, "course", UUID.randomUUID())).isFalse();
  }

  @Test
  void grantsAreValidated() throws Exception {
    var learner = who(sub());
    String id = userId(learner);
    String url = "/api/v1/admin/billing/users/" + id + "/access-grants";
    String plan = plan(true);
    call(
            post(url),
            admin,
            "{\"planId\":\"%s\",\"days\":0,\"reason\":\"valid reason\"}".formatted(plan))
        .andExpect(status().isBadRequest());
    call(
            post(url),
            admin,
            "{\"planId\":\"%s\",\"days\":3661,\"reason\":\"valid reason\"}".formatted(plan))
        .andExpect(status().isBadRequest());
    call(post(url), admin, "{\"planId\":\"%s\",\"days\":5,\"reason\":\"no\"}".formatted(plan))
        .andExpect(status().isBadRequest());
    call(
            post(url),
            admin,
            "{\"planId\":\"%s\",\"days\":5,\"reason\":\"valid reason\"}"
                .formatted(UUID.randomUUID()))
        .andExpect(status().isNotFound());
    call(
            post("/api/v1/admin/billing/users/" + UUID.randomUUID() + "/access-grants"),
            admin,
            "{\"planId\":\"%s\",\"days\":5,\"reason\":\"valid reason\"}".formatted(plan))
        .andExpect(status().isNotFound());
    call(
            post(url),
            admin,
            "{\"planId\":\"%s\",\"days\":5,\"reason\":\"valid reason\"}".formatted(plan(false)))
        .andExpect(status().isConflict()); // plan grants nothing
    String inactive = plan(true);
    call(patch("/api/v1/admin/billing/plans/" + inactive), admin, "{\"active\":false}")
        .andExpect(status().isOk());
    call(
            post(url),
            admin,
            "{\"planId\":\"%s\",\"days\":5,\"reason\":\"valid reason\"}".formatted(inactive))
        .andExpect(status().isConflict());
    lesson(learner).andExpect(status().isForbidden()); // nothing was granted by any failed call
  }

  @Test
  void staffKeepTheirRoleBasedPremiumAccess() throws Exception {
    lesson(admin).andExpect(status().isOk());
    lesson(manager).andExpect(status().isOk());
    problem(admin).andExpect(status().isOk());
    lesson(who(sub(), "MODERATOR")).andExpect(status().isForbidden());
    lesson(null).andExpect(status().isForbidden());
  }

  // ------------------------------------------------------------------ M6.3: the billing summary

  private void period(String userId, String planId, long startOffsetHours, long endOffsetHours) {
    insertPeriod(userId, planId, startOffsetHours, endOffsetHours, false);
  }

  private void insertPeriod(
      String userId, String planId, long startOffsetHours, long endOffsetHours, boolean revoked) {
    // one shared hour-aligned base so periods meant to touch really touch
    java.time.Instant now =
        java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.HOURS);
    jdbc.sql(
            """
            insert into billing.access_periods (id, user_id, plan_id, starts_at, ends_at, source_type, reason, revoked_at, revoked_by, created_at)
            values (:id, :u, :p, :s, :e, 'admin_grant', 'summary test', :ra, :rb, :now)
            """)
        .param("id", Ids.newId())
        .param("u", UUID.fromString(userId))
        .param("p", UUID.fromString(planId))
        .param("s", java.sql.Timestamp.from(now.plus(java.time.Duration.ofHours(startOffsetHours))))
        .param("e", java.sql.Timestamp.from(now.plus(java.time.Duration.ofHours(endOffsetHours))))
        .param("ra", revoked ? java.sql.Timestamp.from(now) : null, java.sql.Types.TIMESTAMP)
        .param("rb", revoked ? Ids.newId() : null, java.sql.Types.OTHER)
        .param("now", java.sql.Timestamp.from(now))
        .update();
  }

  private JsonNode summary(JwtRequestPostProcessor u) throws Exception {
    return json(call(get("/api/v1/me/billing"), u, null).andExpect(status().isOk()));
  }

  @Test
  void summaryNeedsAuthentication() throws Exception {
    call(get("/api/v1/me/billing"), null, null).andExpect(status().isUnauthorized());
  }

  @Test
  void summaryIsNeverCachedAndStartsAsNone() throws Exception {
    JwtRequestPostProcessor u = who(sub());
    call(get("/api/v1/me/billing"), u, null)
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")));
    JsonNode s = summary(u);
    assertThat(s.get("status").asString()).isEqualTo("NONE");
    assertThat(s.get("premium").asBoolean()).isFalse();
    assertThat(s.get("renewalDue").asBoolean()).isFalse();
    assertThat(s.path("validThrough").isMissingNode()).isTrue();
    assertThat(s.get("payments")).isEmpty();
    assertThat(s.get("renewalWindowDays").asInt()).isEqualTo(7);
  }

  @Test
  void statusFollowsTheRenewalWindowBoundary() throws Exception {
    String plan = plan(true);
    // 8 days left: still ACTIVE; 7 days left: inside the window; 3 days: expiring.
    for (Object[] c :
        new Object[][] {
          {8 * 24L, "ACTIVE", false, 8},
          {7 * 24L, "EXPIRING_SOON", true, 7},
          {3 * 24L, "EXPIRING_SOON", true, 3},
        }) {
      JwtRequestPostProcessor u = who(sub());
      String id = userId(u);
      period(id, plan, -1, (Long) c[0]);
      JsonNode s = summary(u);
      assertThat(s.get("status").asString()).as("%s hours", c[0]).isEqualTo(c[1]);
      assertThat(s.get("renewalDue").asBoolean()).isEqualTo(c[2]);
      assertThat(s.get("premium").asBoolean()).isTrue();
      assertThat(s.get("daysRemaining").asInt()).isEqualTo(c[3]);
    }
  }

  @Test
  void expiredAndRevokedOnlyAreDistinguished() throws Exception {
    String plan = plan(true);
    JwtRequestPostProcessor ended = who(sub());
    period(userId(ended), plan, -72, -1);
    JsonNode s = summary(ended);
    assertThat(s.get("status").asString()).isEqualTo("EXPIRED");
    assertThat(s.get("premium").asBoolean()).isFalse();
    assertThat(s.path("daysRemaining").isMissingNode()).isTrue();
    assertThat(s.get("renewalDue").asBoolean()).isFalse();

    JwtRequestPostProcessor revoked = who(sub());
    insertPeriod(userId(revoked), plan, -1, 240, true);
    assertThat(summary(revoked).get("status").asString()).isEqualTo("NONE");
    // revoked AND already ended: still NONE, a revoked period is not "expired access"
    JwtRequestPostProcessor revokedEnded = who(sub());
    insertPeriod(userId(revokedEnded), plan, -72, -1, true);
    assertThat(summary(revokedEnded).get("status").asString()).isEqualTo("NONE");

    // a period of a plan WITHOUT the premium entitlement never counts
    JwtRequestPostProcessor other = who(sub());
    period(userId(other), plan(false), -1, 240);
    assertThat(summary(other).get("status").asString()).isEqualTo("NONE");
  }

  @Test
  void queuedPurchaseExtendsTheDisplayedDateButAGapDoesNot() throws Exception {
    String plan = plan(true);
    JwtRequestPostProcessor chained = who(sub());
    String c = userId(chained);
    period(c, plan, -1, 48); // running, ends in 2 days
    period(c, plan, 48, 48 + 30 * 24); // queued to start when the first ends
    JsonNode s = summary(chained);
    assertThat(s.get("status").asString()).isEqualTo("ACTIVE"); // 32 days away, not "expiring"
    assertThat(s.get("daysRemaining").asInt()).isEqualTo(32);
    assertThat(json(call(get("/api/v1/me/access"), chained, null)).get("validThrough").asString())
        .isEqualTo(s.get("validThrough").asString());

    JwtRequestPostProcessor gap = who(sub());
    String g = userId(gap);
    period(g, plan, -1, 48);
    period(g, plan, 48 + 5 * 24, 48 + 35 * 24); // starts 5 days after the first ends
    JsonNode t = summary(gap);
    assertThat(t.get("status").asString()).isEqualTo("EXPIRING_SOON");
    assertThat(t.get("daysRemaining").asInt()).isEqualTo(2);
  }

  @Test
  void paymentHistoryIsOwnNewestFirstAndCarriesNoProviderReferences() throws Exception {
    String plan = plan(true);
    String price =
        json(call(
                    post("/api/v1/admin/billing/plans/" + plan + "/prices"),
                    admin,
                    "{\"billingMode\":\"ONE_TIME\",\"periodUnit\":\"month\",\"amountInclTaxMinor\":29900}")
                .andExpect(status().isCreated()))
            .get("prices")
            .get(0)
            .get("id")
            .asString();
    JwtRequestPostProcessor mine = who(sub());
    JwtRequestPostProcessor theirs = who(sub());
    String m = userId(mine);
    String t = userId(theirs);
    payment(m, plan, price, "pay_old_" + Ids.newId(), -48);
    payment(m, plan, price, "pay_new_" + Ids.newId(), -1);
    payment(t, plan, price, "pay_other_" + Ids.newId(), -2);

    var res = call(get("/api/v1/me/billing"), mine, null).andExpect(status().isOk());
    String raw = res.andReturn().getResponse().getContentAsString();
    JsonNode s = mapper.readTree(raw);
    assertThat(s.get("payments")).hasSize(2);
    assertThat(s.get("payments").get(0).get("amountMinor").asLong()).isEqualTo(29900);
    assertThat(s.get("payments").get(0).get("currency").asString()).isEqualTo("INR");
    assertThat(s.get("payments").get(0).get("capturedAt").asString())
        .isGreaterThan(s.get("payments").get(1).get("capturedAt").asString());
    assertThat(raw).doesNotContain("pay_").doesNotContain("order_").doesNotContain(t);
    assertThat(summary(theirs).get("payments")).hasSize(1);
  }

  private void payment(String userId, String planId, String priceId, String ref, long hours) {
    java.time.Instant at = java.time.Instant.now().plus(java.time.Duration.ofHours(hours));
    UUID intent = Ids.newId();
    jdbc.sql(
            """
            insert into billing.purchase_intents (id, user_id, plan_id, plan_price_id, billing_mode, period_unit, currency, amount_minor, status, provider, provider_order_ref, created_at, expires_at, updated_at)
            values (:i, :u, :p, :pp, 'ONE_TIME', 'month', 'INR', 29900, 'paid', 'razorpay', :o, :at, :at, :at)
            """)
        .param("i", intent)
        .param("u", UUID.fromString(userId))
        .param("p", UUID.fromString(planId))
        .param("pp", UUID.fromString(priceId))
        .param("o", "order_" + ref)
        .param("at", java.sql.Timestamp.from(at))
        .update();
    jdbc.sql(
            """
            insert into billing.payments (id, intent_id, user_id, provider, provider_payment_ref, amount_minor, currency, status, captured_at, created_at)
            values (:id, :i, :u, 'razorpay', :r, 29900, 'INR', 'captured', :at, :at)
            """)
        .param("id", Ids.newId())
        .param("i", intent)
        .param("u", UUID.fromString(userId))
        .param("r", ref)
        .param("at", java.sql.Timestamp.from(at))
        .update();
  }
}
