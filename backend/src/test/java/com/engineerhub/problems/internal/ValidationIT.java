package com.engineerhub.problems.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engineerhub.problems.ProblemValidationApi;
import com.engineerhub.problems.ProblemValidationApi.Counterexample;
import com.engineerhub.problems.ProblemValidationApi.Outcome;
import com.engineerhub.shared.id.Ids;
import com.engineerhub.testsupport.IntegrationTest;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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

/** The validation harness: authoring side, and the trust boundary that decides "verified". */
@IntegrationTest
class ValidationIT {

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired ObjectMapper mapper;
  @Autowired FakeValidationExecutor executor;
  @Autowired ProblemValidationApi api;

  private static final String HIDDEN = "HIDDEN-VALIDATION-SECRET-7";

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

  @BeforeEach
  void reset() {
    executor.reset();
  }

  private ResultActions call(
      MockHttpServletRequestBuilder b, JwtRequestPostProcessor who, String body) throws Exception {
    MockHttpServletRequestBuilder req = who == null ? b : b.with(who);
    return mvc.perform(
        body == null ? req : req.contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private JsonNode json(ResultActions r) throws Exception {
    return mapper.readTree(r.andReturn().getResponse().getContentAsString());
  }

  private static String program(String language, String source) {
    return "{\"language\":\"%s\",\"source\":\"%s\"}".formatted(language, source);
  }

  /**
   * A problem with statement, one sample + one hidden test and a source: everything except
   * verification.
   */
  private String problem() throws Exception {
    String slug = "v-" + Ids.newId().toString().substring(24);
    String id =
        json(call(
                    post("/api/v1/admin/problems"),
                    admin,
                    "{\"slug\":\"%s\",\"title\":\"V\",\"difficulty\":\"easy\",\"accessTier\":\"free\"}"
                        .formatted(slug))
                .andExpect(status().isCreated()))
            .get("id")
            .asString();
    int v = json(call(get("/api/v1/admin/problems/" + id), admin, null)).get("version").asInt();
    call(
            patch("/api/v1/admin/problems/" + id),
            admin,
            "{\"statement\":\"s\",\"inputFormat\":\"i\",\"outputFormat\":\"o\",\"expectedVersion\":%d}"
                .formatted(v))
        .andExpect(status().isOk());
    call(
            put("/api/v1/admin/problems/" + id + "/tests"),
            admin,
            "{\"tests\":[{\"input\":\"1\",\"expected\":\"1\",\"sample\":true},{\"input\":\"2\",\"expected\":\"%s\",\"sample\":false}],\"expectedTestcaseVersion\":0}"
                .formatted(HIDDEN))
        .andExpect(status().isOk());
    call(put("/api/v1/admin/problems/" + id + "/source"), admin, "{\"kind\":\"original\"}")
        .andExpect(status().isOk());
    return id;
  }

  private void programs(String id) throws Exception {
    for (String role : new String[] {"reference", "brute", "generator"}) {
      call(
              put("/api/v1/admin/problems/" + id + "/validation/programs/" + role),
              admin,
              program("python", "print(" + role.length() + ")"))
          .andExpect(status().isOk());
    }
  }

  private String run(String id, int cases) throws Exception {
    return json(call(
                post("/api/v1/admin/problems/" + id + "/validation/runs"),
                admin,
                "{\"generatedCases\":" + cases + "}")
            .andExpect(status().isCreated()))
        .get("id")
        .asString();
  }

  private String verification(String id) throws Exception {
    return json(call(get("/api/v1/admin/problems/" + id), admin, null))
        .get("validationStatus")
        .asString();
  }

  private String runStatus(String runId) {
    return jdbc.sql("select status from problems.validation_runs where id = :id")
        .param("id", UUID.fromString(runId))
        .query(String.class)
        .single();
  }

  private static Outcome pass(int tests, int generated) {
    return new Outcome(true, tests, generated, "ok", null, "fake-judge");
  }

  /**
   * Verifies a problem through the real path: executor available, run, started, complete passing
   * outcome.
   */
  private void verify(String id) throws Exception {
    executor.available = true;
    programs(id);
    String runId = run(id, 5);
    api.started(UUID.fromString(runId), "fake-judge");
    assertThat(api.accept(UUID.fromString(runId), pass(2, 5))).isTrue();
    executor.available = false;
  }

  // ------------------------------------------------------------------ authoring side

  @Test
  void everyEndpointEnforcesProblemEditOnThatProblem() throws Exception {
    String id = problem();
    String base = "/api/v1/admin/problems/" + id + "/validation";
    record Case(String name, MockHttpServletRequestBuilder req, String body) {}
    List<Case> cases =
        List.of(
            new Case("view", get(base), null),
            new Case("program", put(base + "/programs/reference"), program("python", "print(1)")),
            new Case("run", post(base + "/runs"), "{\"generatedCases\":3}"));

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
      assertThat(
              call(k.req(), who(setterSub, "PROBLEM_SETTER"), k.body())
                  .andReturn()
                  .getResponse()
                  .getStatus())
          .as("assigned setter: " + k.name())
          .isNotIn(401, 403);
      assertThat(call(k.req(), admin, k.body()).andReturn().getResponse().getStatus())
          .as("admin: " + k.name())
          .isNotIn(401, 403);
    }
  }

  @Test
  void programsAreValidatedStoredAsTextAndAuditedWithoutTheirSource() throws Exception {
    String id = problem();
    String url = "/api/v1/admin/problems/" + id + "/validation/programs/";
    call(put(url + "reference"), admin, program("ruby", "puts 1"))
        .andExpect(status().isBadRequest());
    call(put(url + "reference"), admin, program("python", "x".repeat(64_001)))
        .andExpect(status().isBadRequest());
    call(put(url + "reference"), admin, "{\"language\":\"python\",\"source\":\"a\\u0000b\"}")
        .andExpect(status().isBadRequest());
    call(put(url + "reference"), admin, program("python", " ")).andExpect(status().isBadRequest());
    call(put(url + "wizard"), admin, program("python", "print(1)"))
        .andExpect(status().isNotFound());

    String hostile = "<script>alert(1)</script>";
    call(put(url + "reference"), admin, program("python", "print('" + hostile + "')"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.programs[0].source").value("print('" + hostile + "')"));
    String sha1 =
        json(call(get("/api/v1/admin/problems/" + id + "/validation"), admin, null))
            .get("programs")
            .get(0)
            .get("sha256")
            .asString();
    call(put(url + "reference"), admin, program("python", "print(2)")).andExpect(status().isOk());
    String sha2 =
        json(call(get("/api/v1/admin/problems/" + id + "/validation"), admin, null))
            .get("programs")
            .get(0)
            .get("sha256")
            .asString();
    assertThat(sha1).hasSize(64).isNotEqualTo(sha2);

    String details =
        jdbc.sql(
                "select details_after from admin.audit_logs where action = 'problem.validation.program.updated' and resource_id = :r order by id desc limit 1")
            .param("r", id)
            .query(String.class)
            .single();
    assertThat(details).contains("reference").contains(sha2).doesNotContain("print(2)");
  }

  @Test
  void runsNeedAllProgramsAndTestsAndRespectLimits() throws Exception {
    String id = problem();
    String runs = "/api/v1/admin/problems/" + id + "/validation/runs";
    call(post(runs), admin, "{\"generatedCases\":5}")
        .andExpect(status().isConflict()); // no programs
    call(
            put("/api/v1/admin/problems/" + id + "/validation/programs/reference"),
            admin,
            program("python", "print(1)"))
        .andExpect(status().isOk());
    call(post(runs), admin, "{\"generatedCases\":5}")
        .andExpect(status().isConflict()); // brute + generator missing
    programs(id);
    call(post(runs), admin, "{\"generatedCases\":0}").andExpect(status().isBadRequest());
    call(post(runs), admin, "{\"generatedCases\":501}").andExpect(status().isBadRequest());

    // no executor: recorded as unavailable and nothing is verified
    call(post(runs), admin, "{\"generatedCases\":5}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("unavailable"))
        .andExpect(
            jsonPath("$.summary")
                .value(org.hamcrest.Matchers.containsString("No code execution backend")));
    assertThat(verification(id)).isEqualTo("unverified");
    call(get("/api/v1/admin/problems/" + id + "/validation"), admin, null)
        .andExpect(jsonPath("$.executorAvailable").value(false));

    // runs per hour are limited (1 done above)
    for (int i = 0; i < 9; i++) {
      call(post(runs), admin, "{\"generatedCases\":5}").andExpect(status().isCreated());
    }
    call(post(runs), admin, "{\"generatedCases\":5}").andExpect(status().isTooManyRequests());
  }

  @Test
  void aProblemWithoutTestsCannotBeValidated() throws Exception {
    String slug = "v-" + Ids.newId().toString().substring(24);
    String id =
        json(call(
                post("/api/v1/admin/problems"),
                admin,
                "{\"slug\":\"%s\",\"title\":\"V\",\"difficulty\":\"easy\",\"accessTier\":\"free\"}"
                    .formatted(slug)))
            .get("id")
            .asString();
    programs(id);
    call(post("/api/v1/admin/problems/" + id + "/validation/runs"), admin, "{\"generatedCases\":5}")
        .andExpect(status().isConflict());
  }

  // ------------------------------------------------------------------ executor contract

  @Test
  void theExecutorGetsAnExactJobWithoutHiddenTestDataAndOnlyOneRunIsActive() throws Exception {
    String id = problem();
    programs(id);
    executor.available = true;
    String runId = run(id, 7);
    assertThat(executor.jobs).hasSize(1);
    var job = executor.jobs.get(0);
    assertThat(job.runId()).isEqualTo(UUID.fromString(runId));
    assertThat(job.problemId()).isEqualTo(UUID.fromString(id));
    assertThat(job.testcaseVersion()).isEqualTo(1);
    assertThat(job.generatedCases()).isEqualTo(7);
    assertThat(job.checkerType()).isEqualTo("exact");
    assertThat(job.programs()).containsOnlyKeys("reference", "brute", "generator");
    assertThat(job.toString()).doesNotContain(HIDDEN).doesNotContain("\"2\"");
    assertThat(api.job(UUID.fromString(runId))).isPresent();
    // the executor fetches the tests separately, for exactly that version
    assertThat(api.tests(UUID.fromString(id), 1))
        .hasSize(2)
        .anyMatch(t -> t.expected().equals(HIDDEN) && !t.sample());

    call(post("/api/v1/admin/problems/" + id + "/validation/runs"), admin, "{\"generatedCases\":3}")
        .andExpect(status().isConflict()); // one active run
    api.started(UUID.fromString(runId), "fake-judge");
    assertThat(runStatus(runId)).isEqualTo("running");
  }

  @Test
  void aCompletePassingOutcomeVerifiesAndUnlocksPublishing() throws Exception {
    String id = problem();
    call(
            post("/api/v1/admin/problems/" + id + "/publish"),
            admin,
            "{\"originalityConfirmed\":true}")
        .andExpect(status().isConflict()); // not verified
    verify(id);
    assertThat(verification(id)).isEqualTo("verified");
    call(
            post("/api/v1/admin/problems/" + id + "/publish"),
            admin,
            "{\"originalityConfirmed\":true}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("published"));
    assertThat(
            jdbc.sql(
                    "select count(*) from shared.outbox where event_type = 'problems.validation.completed' and aggregate_id = :a")
                .param("a", id)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    call(get("/api/v1/admin/problems/" + id + "/validation"), admin, null)
        .andExpect(jsonPath("$.runs[0].status").value("passed"))
        .andExpect(jsonPath("$.runs[0].testsChecked").value(2));
  }

  // ------------------------------------------------------------------ the trust boundary

  @Test
  void incompleteOrInconsistentSuccessesNeverVerify() throws Exception {
    String id = problem();
    executor.available = true;
    programs(id);
    for (Outcome bad :
        List.of(
            pass(1, 5),
            pass(2, 4),
            pass(0, 0),
            pass(3, 5),
            new Outcome(true, 2, 5, "ok", new Counterexample("x", "1", "1", "2"), "fake-judge"))) {
      String runId = run(id, 5);
      assertThat(api.accept(UUID.fromString(runId), bad)).isTrue();
      assertThat(runStatus(runId)).isEqualTo("error");
      assertThat(verification(id)).as("not verified after " + bad).isEqualTo("unverified");
    }
  }

  @Test
  void aFailingOutcomeStoresABoundedSanitisedCounterexample() throws Exception {
    String id = problem();
    executor.available = true;
    programs(id);
    String runId = run(id, 5);
    var ce = new Counterexample("generated", "a\u0000b" + "x".repeat(10_000), "1", "2\u0007");
    assertThat(
            api.accept(
                UUID.fromString(runId),
                new Outcome(false, 2, 3, "mismatch on case 4", ce, "fake-judge")))
        .isTrue();
    assertThat(runStatus(runId)).isEqualTo("failed");
    String stored =
        jdbc.sql("select counterexample from problems.validation_runs where id = :id")
            .param("id", UUID.fromString(runId))
            .query(String.class)
            .single();
    assertThat(stored.length()).isLessThan(2_500 + 200);
    assertThat(stored).doesNotContain("\\u0000").doesNotContain("\\u0007");
    assertThat(verification(id)).isEqualTo("unverified");
    call(get("/api/v1/admin/problems/" + id + "/validation"), admin, null)
        .andExpect(jsonPath("$.runs[0].summary").value("mismatch on case 4"));
  }

  @Test
  void unknownFinishedAndRepeatedOutcomesAreIgnored() throws Exception {
    String id = problem();
    executor.available = true;
    programs(id);
    assertThat(api.accept(UUID.randomUUID(), pass(2, 5))).isFalse();
    String runId = run(id, 5);
    assertThat(api.accept(UUID.fromString(runId), pass(2, 5))).isTrue();
    assertThat(api.accept(UUID.fromString(runId), pass(2, 5)))
        .as("a finished run cannot be applied twice")
        .isFalse();
    assertThat(api.job(UUID.fromString(runId))).as("no job for a finished run").isEmpty();
  }

  @Test
  void editsMadeWhileARunIsActiveMakeItsResultVoid() throws Exception {
    // tests replaced
    String a = problem();
    executor.available = true;
    programs(a);
    String runA = run(a, 5);
    call(
            put("/api/v1/admin/problems/" + a + "/tests"),
            admin,
            "{\"tests\":[{\"input\":\"1\",\"expected\":\"1\",\"sample\":true},{\"input\":\"2\",\"expected\":\"2\",\"sample\":false}],\"expectedTestcaseVersion\":1}")
        .andExpect(status().isOk());
    assertThat(runStatus(runA)).isEqualTo("stale");
    assertThat(api.accept(UUID.fromString(runA), pass(2, 5))).isFalse();
    assertThat(verification(a)).isEqualTo("unverified");

    // a program replaced
    String b = problem();
    programs(b);
    String runB = run(b, 5);
    call(
            put("/api/v1/admin/problems/" + b + "/validation/programs/brute"),
            admin,
            program("python", "print(99)"))
        .andExpect(status().isOk());
    assertThat(runStatus(runB)).isEqualTo("stale");
    assertThat(api.accept(UUID.fromString(runB), pass(2, 5))).isFalse();

    // limits changed
    String c = problem();
    programs(c);
    String runC = run(c, 5);
    int v = json(call(get("/api/v1/admin/problems/" + c), admin, null)).get("version").asInt();
    call(
            patch("/api/v1/admin/problems/" + c),
            admin,
            "{\"timeLimitMs\":4000,\"expectedVersion\":%d}".formatted(v))
        .andExpect(status().isOk());
    assertThat(runStatus(runC)).isEqualTo("stale");
  }

  @Test
  void hashAndVersionMismatchesAreCaughtEvenWithoutTheInvalidationHooks() throws Exception {
    // simulate a race: data changes behind the service's back after the run was requested
    String a = problem();
    executor.available = true;
    programs(a);
    String runA = run(a, 5);
    jdbc.sql(
            "update problems.validation_programs set sha256 = repeat('0', 64) where problem_id = :p and role = 'reference'")
        .param("p", UUID.fromString(a))
        .update();
    assertThat(api.accept(UUID.fromString(runA), pass(2, 5))).isFalse();
    assertThat(runStatus(runA)).isEqualTo("stale");
    assertThat(verification(a)).isEqualTo("unverified");

    String b = problem();
    programs(b);
    String runB = run(b, 5);
    jdbc.sql("update problems.problems set testcase_version = testcase_version + 1 where id = :p")
        .param("p", UUID.fromString(b))
        .update();
    assertThat(api.accept(UUID.fromString(runB), pass(2, 5))).isFalse();
    assertThat(runStatus(runB)).isEqualTo("stale");
  }

  @Test
  void anythingThatChangesTheMeaningOfCorrectResetsVerification() throws Exception {
    String id = problem();
    verify(id);
    int v = json(call(get("/api/v1/admin/problems/" + id), admin, null)).get("version").asInt();
    call(
            patch("/api/v1/admin/problems/" + id),
            admin,
            "{\"title\":\"renamed\",\"statement\":\"new text\",\"expectedVersion\":%d}"
                .formatted(v))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validationStatus").value("verified")); // prose only

    call(
            patch("/api/v1/admin/problems/" + id),
            admin,
            "{\"memoryLimitMb\":128,\"expectedVersion\":%d}".formatted(v + 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validationStatus").value("unverified"));

    verify(id);
    call(
            put("/api/v1/admin/problems/" + id + "/validation/programs/generator"),
            admin,
            program("cpp", "int main(){}"))
        .andExpect(status().isOk());
    assertThat(verification(id)).isEqualTo("unverified");

    verify(id);
    int v2 = json(call(get("/api/v1/admin/problems/" + id), admin, null)).get("version").asInt();
    call(
            patch("/api/v1/admin/problems/" + id),
            admin,
            "{\"checkerType\":\"token\",\"expectedVersion\":%d}".formatted(v2))
        .andExpect(status().isOk());
    assertThat(verification(id)).isEqualTo("unverified");
  }
}
