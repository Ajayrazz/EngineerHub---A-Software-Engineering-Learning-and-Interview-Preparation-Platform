package com.engineerhub.problems.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engineerhub.shared.id.Ids;
import com.engineerhub.testsupport.IntegrationTest;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
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

/** Problem authoring: authorization, versioned tests, hidden-test secrecy and the publish gates. */
@IntegrationTest
class ProblemAdminIT {

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired ObjectMapper mapper;

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
  private static final String HIDDEN = "HIDDEN-SECRET-EXPECTED-42";

  private ResultActions call(
      MockHttpServletRequestBuilder b, JwtRequestPostProcessor who, String body) throws Exception {
    MockHttpServletRequestBuilder req = who == null ? b : b.with(who);
    return mvc.perform(
        body == null ? req : req.contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private JsonNode json(ResultActions r) throws Exception {
    return mapper.readTree(r.andReturn().getResponse().getContentAsString());
  }

  private String newProblem() throws Exception {
    String slug = "p-" + Ids.newId().toString().substring(24);
    return json(call(
                post("/api/v1/admin/problems"),
                admin,
                "{\"slug\":\"%s\",\"title\":\"Problem %s\",\"difficulty\":\"easy\",\"accessTier\":\"free\"}"
                    .formatted(slug, slug))
            .andExpect(status().isCreated()))
        .get("id")
        .asString();
  }

  private int version(String id) throws Exception {
    return json(call(get("/api/v1/admin/problems/" + id), admin, null)).get("version").asInt();
  }

  private int tcv(String id) throws Exception {
    return json(call(get("/api/v1/admin/problems/" + id), admin, null))
        .get("testcaseVersion")
        .asInt();
  }

  private void fillStatement(String id) throws Exception {
    call(
            patch("/api/v1/admin/problems/" + id),
            admin,
            "{\"statement\":\"Add two numbers.\",\"inputFormat\":\"Two integers.\",\"outputFormat\":\"Their sum.\",\"constraints\":\"|a|,|b| <= 1000\",\"expectedVersion\":%d}"
                .formatted(version(id)))
        .andExpect(status().isOk());
  }

  private static final String TESTS =
      "[{\"input\":\"1 2\",\"expected\":\"3\",\"sample\":true,\"explanation\":\"1 + 2 = 3\"},{\"input\":\"10 20\",\"expected\":\""
          + HIDDEN
          + "\",\"sample\":false}]";

  private void putTests(String id, String tests) throws Exception {
    call(
            put("/api/v1/admin/problems/" + id + "/tests"),
            admin,
            "{\"tests\":%s,\"expectedTestcaseVersion\":%d}".formatted(tests, tcv(id)))
        .andExpect(status().isOk());
  }

  private void putSource(String id) throws Exception {
    call(put("/api/v1/admin/problems/" + id + "/source"), admin, "{\"kind\":\"original\"}")
        .andExpect(status().isOk());
  }

  private void verify(String id) {
    jdbc.sql("update problems.problems set validation_status = 'verified' where id = :id")
        .param("id", UUID.fromString(id))
        .update();
  }

  private ResultActions publish(String id, boolean confirmed) throws Exception {
    return call(
        post("/api/v1/admin/problems/" + id + "/publish"),
        admin,
        "{\"originalityConfirmed\":" + confirmed + "}");
  }

  private long audits(String action, String id) {
    return jdbc.sql("select count(*) from admin.audit_logs where action = :a and resource_id = :r")
        .param("a", action)
        .param("r", id)
        .query(Long.class)
        .single();
  }

  /** A problem that satisfies every gate except verification. */
  private String readyProblem() throws Exception {
    String id = newProblem();
    fillStatement(id);
    putTests(id, TESTS);
    putSource(id);
    return id;
  }

  // ------------------------------------------------------------------ authorization

  @Test
  void everyEndpointEnforcesItsPermissionForEveryRole() throws Exception {
    String id = readyProblem();
    String base = "/api/v1/admin/problems/" + id;
    record Case(
        String name,
        MockHttpServletRequestBuilder req,
        String body,
        boolean needsPublish,
        boolean needsGlobalEdit) {}
    List<Case> cases =
        List.of(
            new Case("list patterns", get("/api/v1/admin/patterns"), null, false, true),
            new Case(
                "create pattern",
                post("/api/v1/admin/patterns"),
                "{\"slug\":\"pt-" + Ids.newId().toString().substring(24) + "\",\"name\":\"P\"}",
                false,
                true),
            new Case("list problems", get("/api/v1/admin/problems"), null, false, true),
            new Case(
                "create problem",
                post("/api/v1/admin/problems"),
                "{\"slug\":\"x-"
                    + Ids.newId().toString().substring(24)
                    + "\",\"title\":\"t\",\"difficulty\":\"easy\",\"accessTier\":\"free\"}",
                false,
                true),
            new Case("get problem", get(base), null, false, false),
            new Case(
                "patch problem",
                patch(base),
                "{\"title\":\"n\",\"expectedVersion\":-1}",
                false,
                false),
            new Case(
                "tags", put(base + "/tags"), "{\"topicIds\":[],\"patternIds\":[]}", false, false),
            new Case("get tests", get(base + "/tests"), null, false, false),
            new Case(
                "put tests",
                put(base + "/tests"),
                "{\"tests\":[],\"expectedTestcaseVersion\":-1}",
                false,
                false),
            new Case("source", put(base + "/source"), "{\"kind\":\"original\"}", false, false),
            new Case(
                "publish",
                post(base + "/publish"),
                "{\"originalityConfirmed\":false}",
                true,
                false),
            new Case("unpublish", post(base + "/unpublish"), null, true, false),
            new Case("archive", post(base + "/archive"), null, true, false));

    String setterSub = sub();
    UUID setterId =
        UUID.fromString(
            json(mvc.perform(get("/api/v1/me").with(who(setterSub, "PROBLEM_SETTER")))
                    .andExpect(status().isOk()))
                .get("id")
                .asString());
    call(
            post("/api/v1/admin/users/" + setterId + "/grants"),
            who(sub(), "SUPER_ADMIN"),
            "{\"permission\":\"problem.edit\",\"resourceType\":\"problem\",\"resourceId\":\""
                + id
                + "\"}")
        .andExpect(status().isCreated());
    var assigned = who(setterSub, "PROBLEM_SETTER");
    List<JwtRequestPostProcessor> denied =
        List.of(
            who(sub()),
            who(sub(), "MODERATOR"),
            who(sub(), "CONTENT_MANAGER"),
            who(sub(), "INSTRUCTOR"),
            who(sub(), "PROBLEM_SETTER"));

    for (Case k : cases) {
      call(k.req(), null, k.body()).andExpect(status().isUnauthorized());
      for (var d : denied) {
        call(k.req(), d, k.body()).andExpect(status().isForbidden());
      }
      int got = call(k.req(), assigned, k.body()).andReturn().getResponse().getStatus();
      if (!k.needsPublish() && !k.needsGlobalEdit()) {
        assertThat(got).as("assigned setter: " + k.name()).isNotIn(401, 403);
      } else {
        assertThat(got).as("assigned setter: " + k.name()).isEqualTo(403);
      }
      for (var staff : List.of(admin, who(sub(), "SUPER_ADMIN"))) {
        assertThat(call(k.req(), staff, k.body()).andReturn().getResponse().getStatus())
            .as("staff: " + k.name())
            .isNotIn(401, 403);
      }
    }
  }

  @Test
  void aSetterAssignedToOneProblemCannotTouchAnother() throws Exception {
    String mine = newProblem();
    String other = readyProblem();
    String setterSub = sub();
    UUID setterId =
        UUID.fromString(
            json(mvc.perform(get("/api/v1/me").with(who(setterSub, "PROBLEM_SETTER")))
                    .andExpect(status().isOk()))
                .get("id")
                .asString());
    call(
            post("/api/v1/admin/users/" + setterId + "/grants"),
            who(sub(), "SUPER_ADMIN"),
            "{\"permission\":\"problem.edit\",\"resourceType\":\"problem\",\"resourceId\":\""
                + mine
                + "\"}")
        .andExpect(status().isCreated());
    var setter = who(setterSub, "PROBLEM_SETTER");
    call(get("/api/v1/admin/problems/" + other), setter, null).andExpect(status().isForbidden());
    call(get("/api/v1/admin/problems/" + other + "/tests"), setter, null)
        .andExpect(status().isForbidden()); // hidden tests of someone else's problem
    call(get("/api/v1/admin/problems/" + mine + "/tests"), setter, null).andExpect(status().isOk());
  }

  // ------------------------------------------------------------------ publishing

  @Test
  void publishingRequiresVerificationAndTheSnapshotNeverContainsHiddenTests() throws Exception {
    String id = readyProblem();
    publish(id, true)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(Matchers.containsString("not verified")));
    verify(id);
    publish(id, true).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("published"));

    String snapshot =
        jdbc.sql("select published_snapshot::text from problems.problems where id = :id")
            .param("id", UUID.fromString(id))
            .query(String.class)
            .single();
    assertThat(snapshot)
        .contains("1 + 2 = 3")
        .contains("\"input\": \"1 2\"")
        .doesNotContain(HIDDEN)
        .doesNotContain("10 20");
    assertThat(
            jdbc.sql("select published_testcase_version from problems.problems where id = :id")
                .param("id", UUID.fromString(id))
                .query(Integer.class)
                .single())
        .isEqualTo(1);
    assertThat(audits("problem.published", id)).isEqualTo(1);
    assertThat(
            jdbc.sql(
                    "select count(*) from shared.outbox where event_type = 'problems.problem.published' and aggregate_id = :a")
                .param("a", id)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql("select reviewed_at from problems.problem_sources where problem_id = :id")
                .param("id", UUID.fromString(id))
                .query(java.sql.Timestamp.class)
                .optional())
        .isPresent();

    call(post("/api/v1/admin/problems/" + id + "/unpublish"), admin, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("draft"));
    assertThat(
            jdbc.sql("select published_snapshot from problems.problems where id = :id")
                .param("id", UUID.fromString(id))
                .query(String.class)
                .optional())
        .isEmpty();
    call(post("/api/v1/admin/problems/" + id + "/unpublish"), admin, null)
        .andExpect(status().isConflict());
    call(post("/api/v1/admin/problems/" + id + "/archive"), admin, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("archived"));
  }

  @Test
  void eachPublishGateIsEnforcedOnItsOwn() throws Exception {
    // originality not confirmed
    String a = readyProblem();
    verify(a);
    publish(a, false).andExpect(status().isBadRequest());

    // no source
    String b = newProblem();
    fillStatement(b);
    putTests(b, TESTS);
    verify(b);
    publish(b, true)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(Matchers.containsString("source")));

    // no statement
    String c = newProblem();
    putTests(c, TESTS);
    putSource(c);
    verify(c);
    publish(c, true)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(Matchers.containsString("statement")));

    // only samples / only hidden
    String d = newProblem();
    fillStatement(d);
    putSource(d);
    putTests(d, "[{\"input\":\"1\",\"expected\":\"1\",\"sample\":true}]");
    verify(d);
    publish(d, true)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(Matchers.containsString("hidden")));
    putTests(d, "[{\"input\":\"1\",\"expected\":\"1\",\"sample\":false}]");
    verify(d);
    publish(d, true)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(Matchers.containsString("sample")));
  }

  // ------------------------------------------------------------------ tests & versions

  @Test
  void replacingTestsBumpsTheVersionResetsVerificationAndKeepsOldRows() throws Exception {
    String id = readyProblem();
    assertThat(tcv(id)).isEqualTo(1);
    verify(id);
    call(get("/api/v1/admin/problems/" + id), admin, null)
        .andExpect(jsonPath("$.validationStatus").value("verified"));

    putTests(
        id,
        "[{\"input\":\"5 5\",\"expected\":\"10\",\"sample\":true},{\"input\":\"1 1\",\"expected\":\"2\",\"sample\":false}]");
    call(get("/api/v1/admin/problems/" + id), admin, null)
        .andExpect(jsonPath("$.testcaseVersion").value(2))
        .andExpect(jsonPath("$.validationStatus").value("unverified"))
        .andExpect(jsonPath("$.sampleCount").value(1))
        .andExpect(jsonPath("$.hiddenCount").value(1));
    assertThat(
            jdbc.sql(
                    "select count(*) from problems.test_cases where problem_id = :id and version = 1")
                .param("id", UUID.fromString(id))
                .query(Long.class)
                .single())
        .as("version 1 kept for older submissions")
        .isEqualTo(2);

    call(get("/api/v1/admin/problems/" + id + "/tests"), admin, null)
        .andExpect(jsonPath("$.tests.length()").value(2))
        .andExpect(jsonPath("$.tests[0].input").value("5 5"));
    assertThat(audits("problem.tests.replaced", id)).isEqualTo(2);
  }

  @Test
  void staleTestVersionsAndBadTestsAreRejected() throws Exception {
    String id = readyProblem();
    call(
            put("/api/v1/admin/problems/" + id + "/tests"),
            admin,
            "{\"tests\":%s,\"expectedTestcaseVersion\":0}".formatted(TESTS))
        .andExpect(status().isConflict());
    String url = "/api/v1/admin/problems/" + id + "/tests";
    int v = tcv(id);
    call(
            put(url),
            admin,
            "{\"tests\":[{\"input\":\"1\",\"expected\":\"  \",\"sample\":false}],\"expectedTestcaseVersion\":%d}"
                .formatted(v))
        .andExpect(status().isBadRequest());
    call(
            put(url),
            admin,
            "{\"tests\":[{\"input\":\"1\",\"expected\":\"1\",\"sample\":false,\"explanation\":\"leak\"}],\"expectedTestcaseVersion\":%d}"
                .formatted(v))
        .andExpect(status().isBadRequest());
    call(
            put(url),
            admin,
            "{\"tests\":[{\"input\":\"1\\u0000\",\"expected\":\"1\",\"sample\":false}],\"expectedTestcaseVersion\":%d}"
                .formatted(v))
        .andExpect(status().isBadRequest());
    assertThat(tcv(id)).as("failed replacements change nothing").isEqualTo(v);
    call(get(url), admin, null).andExpect(jsonPath("$.tests.length()").value(2));
  }

  @Test
  void hiddenTestsAreNeverPartOfTheProblemView() throws Exception {
    String id = readyProblem();
    String view =
        call(get("/api/v1/admin/problems/" + id), admin, null)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(view).doesNotContain(HIDDEN).contains("\"hiddenCount\":1");
  }

  // ------------------------------------------------------------------ editing

  @Test
  void changingTheCheckerResetsVerificationAndStaleEditsConflict() throws Exception {
    String id = readyProblem();
    verify(id);
    int v = version(id);
    call(
            patch("/api/v1/admin/problems/" + id),
            admin,
            "{\"timeLimitMs\":3000,\"expectedVersion\":%d}".formatted(v))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validationStatus").value("verified"));
    call(
            patch("/api/v1/admin/problems/" + id),
            admin,
            "{\"checkerType\":\"token\",\"expectedVersion\":%d}".formatted(v + 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validationStatus").value("unverified"))
        .andExpect(jsonPath("$.checkerType").value("token"));
    call(
            patch("/api/v1/admin/problems/" + id),
            admin,
            "{\"title\":\"stale\",\"expectedVersion\":%d}".formatted(v))
        .andExpect(status().isConflict());
  }

  @Test
  void invalidEditsAreRejected() throws Exception {
    String id = newProblem();
    int v = version(id);
    for (String body :
        new String[] {
          "{\"timeLimitMs\":5,\"expectedVersion\":%d}",
              "{\"memoryLimitMb\":999999,\"expectedVersion\":%d}",
              "{\"checkerType\":\"magic\",\"expectedVersion\":%d}",
          "{\"difficulty\":\"impossible\",\"expectedVersion\":%d}",
              "{\"hints\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\"],\"expectedVersion\":%d}",
              "{\"statement\":\"bad\\u0000\",\"expectedVersion\":%d}"
        }) {
      call(patch("/api/v1/admin/problems/" + id), admin, body.formatted(v))
          .andExpect(status().isBadRequest());
    }
    call(
            post("/api/v1/admin/problems"),
            admin,
            "{\"slug\":\"Bad Slug\",\"title\":\"t\",\"difficulty\":\"easy\",\"accessTier\":\"free\"}")
        .andExpect(status().isBadRequest());
    String slug =
        json(call(get("/api/v1/admin/problems/" + id), admin, null)).get("slug").asString();
    call(
            post("/api/v1/admin/problems"),
            admin,
            "{\"slug\":\"%s\",\"title\":\"t\",\"difficulty\":\"easy\",\"accessTier\":\"free\"}"
                .formatted(slug))
        .andExpect(status().isConflict());
  }

  @Test
  void markupInTheStatementIsStoredAsText() throws Exception {
    String id = newProblem();
    String hostile = "<script>alert(1)</script> **bold**";
    call(
            patch("/api/v1/admin/problems/" + id),
            admin,
            "{\"statement\":\"%s\",\"expectedVersion\":%d}".formatted(hostile, version(id)))
        .andExpect(status().isOk());
    call(get("/api/v1/admin/problems/" + id), admin, null)
        .andExpect(jsonPath("$.statement").value(hostile));
  }

  // ------------------------------------------------------------------ taxonomy & source

  @Test
  void tagsAreValidatedAndPersisted() throws Exception {
    String id = newProblem();
    UUID topic =
        jdbc.sql("select id from catalog.topics where slug = 'arrays'").query(UUID.class).single();
    String slug = "pt-" + Ids.newId().toString().substring(24);
    String pattern =
        json(call(
                    post("/api/v1/admin/patterns"),
                    admin,
                    "{\"slug\":\"%s\",\"name\":\"Two pointers %s\"}".formatted(slug, slug))
                .andExpect(status().isCreated()))
            .get("id")
            .asString();
    call(
            post("/api/v1/admin/patterns"),
            admin,
            "{\"slug\":\"%s\",\"name\":\"dup\"}".formatted(slug))
        .andExpect(status().isConflict());

    call(
            put("/api/v1/admin/problems/" + id + "/tags"),
            admin,
            "{\"topicIds\":[\"%s\"],\"patternIds\":[\"%s\"]}".formatted(topic, pattern))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.topics[0].slug").value("arrays"))
        .andExpect(jsonPath("$.patterns[0].slug").value(slug));
    call(
            put("/api/v1/admin/problems/" + id + "/tags"),
            admin,
            "{\"topicIds\":[\"%s\"],\"patternIds\":[]}".formatted(UUID.randomUUID()))
        .andExpect(status().isBadRequest());
    call(
            put("/api/v1/admin/problems/" + id + "/tags"),
            admin,
            "{\"topicIds\":[],\"patternIds\":[\"%s\"]}".formatted(UUID.randomUUID()))
        .andExpect(status().isBadRequest());
    call(get("/api/v1/admin/problems/" + id), admin, null)
        .andExpect(jsonPath("$.topics.length()").value(1)); // failed calls changed nothing
    call(
            put("/api/v1/admin/problems/" + id + "/tags"),
            admin,
            "{\"topicIds\":[],\"patternIds\":[]}")
        .andExpect(jsonPath("$.topics.length()").value(0));
  }

  @Test
  void licensedSourcesNeedLicenseAndAttributionAndChangesClearTheSignOff() throws Exception {
    String id = readyProblem();
    String url = "/api/v1/admin/problems/" + id + "/source";
    call(put(url), admin, "{\"kind\":\"open_license\"}").andExpect(status().isBadRequest());
    call(put(url), admin, "{\"kind\":\"original\",\"sourceUrl\":\"javascript:alert(1)\"}")
        .andExpect(status().isBadRequest());
    verify(id);
    publish(id, true).andExpect(status().isOk());
    call(put(url), admin, "{\"kind\":\"ai_assisted\"}").andExpect(status().isOk());
    assertThat(
            jdbc.sql("select reviewed_at from problems.problem_sources where problem_id = :id")
                .param("id", UUID.fromString(id))
                .query(java.sql.Timestamp.class)
                .optional())
        .isEmpty();
  }
}
