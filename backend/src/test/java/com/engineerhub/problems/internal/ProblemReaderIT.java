package com.engineerhub.problems.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

/**
 * The public problem bank: snapshot-only visibility, filters, premium gating, bookmarks, state,
 * random.
 */
@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProblemReaderIT {

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
  private static final String HIDDEN = "HIDDEN-READER-SECRET-9";

  private ResultActions call(
      MockHttpServletRequestBuilder b, JwtRequestPostProcessor who, String body) throws Exception {
    MockHttpServletRequestBuilder req = who == null ? b : b.with(who);
    return mvc.perform(
        body == null ? req : req.contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private JsonNode json(ResultActions r) throws Exception {
    return mapper.readTree(r.andReturn().getResponse().getContentAsString());
  }

  private String tag;
  private String alpha, beta, gamma, delta, archived, edited; // slugs
  private String editedId;
  private String patternSlug;

  /** Creates a complete, verified problem and (optionally) publishes it. */
  private String[] create(
      String slug,
      String title,
      String difficulty,
      String tier,
      String topicSlug,
      boolean withPattern,
      String statement,
      boolean publish)
      throws Exception {
    String id =
        json(call(
                    post("/api/v1/admin/problems"),
                    admin,
                    "{\"slug\":\"%s\",\"title\":\"%s\",\"difficulty\":\"%s\",\"accessTier\":\"%s\"}"
                        .formatted(slug, title, difficulty, tier))
                .andExpect(status().isCreated()))
            .get("id")
            .asString();
    int v = json(call(get("/api/v1/admin/problems/" + id), admin, null)).get("version").asInt();
    call(
            patch("/api/v1/admin/problems/" + id),
            admin,
            "{\"statement\":\"%s\",\"inputFormat\":\"in\",\"outputFormat\":\"out\",\"constraints\":\"c\",\"hints\":[\"first hint\"],\"expectedVersion\":%d}"
                .formatted(statement, v))
        .andExpect(status().isOk());
    call(
            put("/api/v1/admin/problems/" + id + "/tests"),
            admin,
            "{\"tests\":[{\"input\":\"1\",\"expected\":\"1\",\"sample\":true,\"explanation\":\"identity\"},{\"input\":\"2\",\"expected\":\"%s\",\"sample\":false}],\"expectedTestcaseVersion\":0}"
                .formatted(HIDDEN))
        .andExpect(status().isOk());
    UUID topic =
        jdbc.sql("select id from catalog.topics where slug = :s")
            .param("s", topicSlug)
            .query(UUID.class)
            .single();
    String patterns = "[]";
    if (withPattern) {
      patterns =
          "[\""
              + jdbc.sql("select id from problems.patterns where slug = :s")
                  .param("s", patternSlug)
                  .query(UUID.class)
                  .single()
              + "\"]";
    }
    call(
            put("/api/v1/admin/problems/" + id + "/tags"),
            admin,
            "{\"topicIds\":[\"%s\"],\"patternIds\":%s}".formatted(topic, patterns))
        .andExpect(status().isOk());
    call(put("/api/v1/admin/problems/" + id + "/source"), admin, "{\"kind\":\"original\"}")
        .andExpect(status().isOk());
    if (publish) {
      jdbc.sql("update problems.problems set validation_status = 'verified' where id = :id")
          .param("id", UUID.fromString(id))
          .update();
      call(
              post("/api/v1/admin/problems/" + id + "/publish"),
              admin,
              "{\"originalityConfirmed\":true}")
          .andExpect(status().isOk());
    }
    return new String[] {id, slug};
  }

  @BeforeAll
  void fixture() throws Exception {
    tag = "zq" + Ids.newId().toString().substring(26).replaceAll("[^a-z0-9]", "x");
    patternSlug = "pat-" + tag;
    call(
            post("/api/v1/admin/patterns"),
            admin,
            "{\"slug\":\"%s\",\"name\":\"Pattern %s\"}".formatted(patternSlug, tag))
        .andExpect(status().isCreated());
    alpha = "alpha-" + tag;
    beta = "beta-" + tag;
    gamma = "gamma-" + tag;
    delta = "delta-" + tag;
    archived = "archived-" + tag;
    edited = "edited-" + tag;
    create(
        alpha,
        "Alpha " + tag,
        "easy",
        "free",
        "arrays",
        true,
        "ALPHA-STATEMENT <script>alert(1)</script>",
        true);
    create(beta, "Beta " + tag, "medium", "free", "graphs", true, "Beta statement", true);
    create(gamma, "Gamma " + tag, "hard", "premium", "arrays", false, "GAMMA-PREMIUM-SECRET", true);
    create(delta, "Delta " + tag, "easy", "free", "arrays", false, "draft only", false);
    String[] a = create(archived, "Archived " + tag, "easy", "free", "arrays", false, "old", true);
    call(post("/api/v1/admin/problems/" + a[0] + "/archive"), admin, null)
        .andExpect(status().isOk());
    editedId = create(edited, "Edited " + tag, "medium", "free", "graphs", false, "v1", true)[0];
  }

  private JsonNode listed(String query) throws Exception {
    return json(
        call(get("/api/v1/problems?q=" + tag + query), null, null).andExpect(status().isOk()));
  }

  private static java.util.List<String> slugs(JsonNode page) {
    var out = new java.util.ArrayList<String>();
    page.get("items").forEach(i -> out.add(i.get("slug").asString()));
    return out;
  }

  // ------------------------------------------------------------------ list

  @Test
  void listsOnlyPublishedProblemsAndNeverLeaksDraftsArchivedOrHiddenTests() throws Exception {
    String body =
        call(get("/api/v1/problems?q=" + tag), null, null)
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", Matchers.containsString("public")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode page = mapper.readTree(body);
    assertThat(slugs(page)).containsExactlyInAnyOrder(alpha, beta, gamma, edited);
    assertThat(body)
        .doesNotContain(delta)
        .doesNotContain(archived)
        .doesNotContain(HIDDEN)
        .doesNotContain("ALPHA-STATEMENT")
        .doesNotContain("GAMMA-PREMIUM-SECRET");
    assertThat(page.get("total").asInt()).isEqualTo(4);
  }

  @Test
  void unfilteredListingAndRandomPicksNeverSurfaceUnpublishedProblems() throws Exception {
    // no filter at all: nothing else can hide a draft, so only the status check protects it
    for (int page = 0; page < 5; page++) {
      String body =
          call(get("/api/v1/problems?size=50&page=" + page), null, null)
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(body).doesNotContain("\"" + delta + "\"").doesNotContain("\"" + archived + "\"");
    }
    for (int i = 0; i < 60; i++) {
      String slug =
          json(call(get("/api/v1/problems/random"), null, null).andExpect(status().isOk()))
              .get("slug")
              .asString();
      assertThat(slug).isNotIn(delta, archived);
      assertThat(slug)
          .doesNotStartWith("v-")
          .doesNotStartWith("p-"); // drafts created by the other problem tests
    }
  }

  @Test
  void filtersSortingAndPaging() throws Exception {
    assertThat(slugs(listed("&difficulty=easy"))).containsExactly(alpha);
    assertThat(slugs(listed("&difficulty=medium"))).containsExactlyInAnyOrder(beta, edited);
    assertThat(slugs(listed("&tier=premium"))).containsExactly(gamma);
    assertThat(slugs(listed("&topic=graphs"))).containsExactlyInAnyOrder(beta, edited);
    assertThat(slugs(listed("&topic=arrays"))).containsExactlyInAnyOrder(alpha, gamma);
    assertThat(slugs(listed("&pattern=" + patternSlug))).containsExactlyInAnyOrder(alpha, beta);
    assertThat(slugs(listed("&topic=graphs&pattern=" + patternSlug))).containsExactly(beta);
    assertThat(slugs(listed("&sort=title"))).containsExactly(alpha, beta, edited, gamma);
    assertThat(slugs(listed("&sort=difficulty"))).startsWith(alpha).endsWith(gamma);
    assertThat(listed("&size=2&page=1").get("items").size()).isEqualTo(2);
    assertThat(listed("&size=1000").get("size").asInt()).isEqualTo(50);

    call(get("/api/v1/problems?difficulty=impossible"), null, null)
        .andExpect(status().isBadRequest());
    call(get("/api/v1/problems?tier=gold"), null, null).andExpect(status().isBadRequest());
    call(get("/api/v1/problems?topic=Not%20A%20Slug"), null, null)
        .andExpect(status().isBadRequest());
    call(get("/api/v1/problems?pattern=A_B"), null, null).andExpect(status().isBadRequest());
    call(get("/api/v1/problems?sort=random"), null, null).andExpect(status().isBadRequest());
    call(get("/api/v1/problems?q=" + "x".repeat(101)), null, null)
        .andExpect(status().isBadRequest());
    // wildcards search literally
    call(get("/api/v1/problems?q=%25"), null, null).andExpect(jsonPath("$.total").value(0));
    call(get("/api/v1/problems?q=_"), null, null).andExpect(jsonPath("$.total").value(0));
  }

  @Test
  void theListFollowsThePublishedSnapshotNotTheDraft() throws Exception {
    int v =
        json(call(get("/api/v1/admin/problems/" + editedId), admin, null)).get("version").asInt();
    call(
            patch("/api/v1/admin/problems/" + editedId),
            admin,
            "{\"title\":\"Renamed %s\",\"difficulty\":\"hard\",\"statement\":\"v2 draft\",\"expectedVersion\":%d}"
                .formatted(tag, v))
        .andExpect(status().isOk());
    try {
      assertThat(slugs(listed("&difficulty=medium"))).contains(edited); // still medium
      assertThat(slugs(listed("&difficulty=hard"))).doesNotContain(edited);
      call(get("/api/v1/problems/" + edited), null, null)
          .andExpect(jsonPath("$.title").value("Edited " + tag))
          .andExpect(jsonPath("$.statement").value("v1"));
      // publishing the change makes it visible
      call(
              post("/api/v1/admin/problems/" + editedId + "/publish"),
              admin,
              "{\"originalityConfirmed\":true}")
          .andExpect(status().isOk());
      call(get("/api/v1/problems/" + edited), null, null)
          .andExpect(jsonPath("$.statement").value("v2 draft"));
    } finally {
      int v2 =
          json(call(get("/api/v1/admin/problems/" + editedId), admin, null)).get("version").asInt();
      call(
              patch("/api/v1/admin/problems/" + editedId),
              admin,
              "{\"title\":\"Edited %s\",\"difficulty\":\"medium\",\"statement\":\"v1\",\"expectedVersion\":%d}"
                  .formatted(tag, v2))
          .andExpect(status().isOk());
      call(
              post("/api/v1/admin/problems/" + editedId + "/publish"),
              admin,
              "{\"originalityConfirmed\":true}")
          .andExpect(status().isOk());
    }
  }

  // ------------------------------------------------------------------ detail

  @Test
  void detailShowsSamplesOnlyAndKeepsMarkupInert() throws Exception {
    String body =
        call(get("/api/v1/problems/" + alpha), null, null)
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", Matchers.containsString("public")))
            .andExpect(jsonPath("$.samples.length()").value(1))
            .andExpect(jsonPath("$.samples[0].explanation").value("identity"))
            .andExpect(jsonPath("$.hints[0]").value("first hint"))
            .andExpect(jsonPath("$.timeLimitMs").value(2000))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).doesNotContain(HIDDEN).doesNotContain("\"2\"");
    assertThat(mapper.readTree(body).get("statement").asString())
        .isEqualTo("ALPHA-STATEMENT <script>alert(1)</script>");
  }

  @Test
  void unknownDraftArchivedAndMalformedSlugsAreNotFound() throws Exception {
    for (String s : new String[] {delta, archived, "does-not-exist", "UPPER", "a--b"}) {
      assertThat(
              call(get("/api/v1/problems/" + s), null, null).andReturn().getResponse().getStatus())
          .as(s)
          .isEqualTo(404);
    }
  }

  @Test
  void premiumStatementsAreWithheldFromEveryoneWithoutEntitlement() throws Exception {
    for (JwtRequestPostProcessor who :
        new JwtRequestPostProcessor[] {
          null, who(sub()), who(sub(), "MODERATOR"), who(sub(), "PROBLEM_SETTER")
        }) {
      String body =
          call(get("/api/v1/problems/" + gamma), who, null)
              .andExpect(status().isForbidden())
              .andExpect(jsonPath("$.code").value("ENTITLEMENT_REQUIRED"))
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(body).doesNotContain("GAMMA-PREMIUM-SECRET");
    }
    call(get("/api/v1/problems/" + gamma), who(sub(), "CONTENT_MANAGER"), null)
        .andExpect(status().isOk()); // staff with content.premium.read
    call(get("/api/v1/problems/" + gamma), admin, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statement").value("GAMMA-PREMIUM-SECRET"))
        .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")));
    // the list still advertises it (metadata only)
    assertThat(slugs(listed("&tier=premium"))).containsExactly(gamma);
  }

  // ------------------------------------------------------------------ patterns & random

  @Test
  void patternsListOnlyThoseWithPublishedProblems() throws Exception {
    String orphan = "orphan-" + tag;
    call(
            post("/api/v1/admin/patterns"),
            admin,
            "{\"slug\":\"%s\",\"name\":\"Orphan %s\"}".formatted(orphan, tag))
        .andExpect(status().isCreated());
    String body =
        call(get("/api/v1/patterns"), null, null)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode all = mapper.readTree(body);
    var found = new java.util.ArrayList<String>();
    all.forEach(p -> found.add(p.get("slug").asString()));
    assertThat(found).contains(patternSlug).doesNotContain(orphan);
    all.forEach(
        p -> {
          if (p.get("slug").asString().equals(patternSlug)) {
            assertThat(p.get("problemCount").asInt()).isEqualTo(2);
          }
        });
  }

  @Test
  void randomPicksOnlyMatchingPublishedProblems() throws Exception {
    for (int i = 0; i < 8; i++) {
      String slug =
          json(call(
                      get(
                          "/api/v1/problems/random?difficulty=easy&topic=arrays&tier=free&pattern="
                              + patternSlug),
                      null,
                      null)
                  .andExpect(status().isOk())
                  .andExpect(header().string("Cache-Control", Matchers.containsString("no-store"))))
              .get("slug")
              .asString();
      assertThat(slug).isEqualTo(alpha);
    }
    call(
            get("/api/v1/problems/random?difficulty=hard&tier=free&pattern=" + patternSlug),
            null,
            null)
        .andExpect(status().isNotFound());
    call(get("/api/v1/problems/random?difficulty=bad"), null, null)
        .andExpect(status().isBadRequest());
    // drafts and archived problems are never picked
    for (int i = 0; i < 20; i++) {
      String slug =
          json(call(get("/api/v1/problems/random?topic=arrays"), null, null))
              .get("slug")
              .asString();
      assertThat(slug).isNotIn(delta, archived);
    }
  }

  // ------------------------------------------------------------------ the caller's own state

  @Test
  void bookmarksNeedLoginAreIdempotentPerUserAndOnlyForPublishedProblems() throws Exception {
    call(put("/api/v1/problems/" + alpha + "/bookmark"), null, null)
        .andExpect(status().isUnauthorized());
    call(get("/api/v1/me/bookmarks"), null, null).andExpect(status().isUnauthorized());
    call(get("/api/v1/me/problems/states"), null, null).andExpect(status().isUnauthorized());

    var alice = who(sub());
    var bob = who(sub());
    call(put("/api/v1/problems/" + alpha + "/bookmark"), alice, null)
        .andExpect(status().isNoContent());
    call(put("/api/v1/problems/" + alpha + "/bookmark"), alice, null)
        .andExpect(status().isNoContent()); // idempotent
    call(put("/api/v1/problems/" + delta + "/bookmark"), alice, null)
        .andExpect(status().isNotFound());
    call(put("/api/v1/problems/" + archived + "/bookmark"), alice, null)
        .andExpect(status().isNotFound());
    call(put("/api/v1/problems/nope/bookmark"), alice, null).andExpect(status().isNotFound());

    call(get("/api/v1/me/bookmarks"), alice, null)
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].slug").value(alpha))
        .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")));
    call(get("/api/v1/me/bookmarks"), bob, null)
        .andExpect(jsonPath("$.length()").value(0)); // isolation
    call(get("/api/v1/me/problems/states"), bob, null)
        .andExpect(jsonPath("$.states.length()").value(0));

    call(delete("/api/v1/problems/" + alpha + "/bookmark"), bob, null)
        .andExpect(status().isNoContent()); // nothing of bob's: no effect on alice
    call(get("/api/v1/me/bookmarks"), alice, null).andExpect(jsonPath("$.length()").value(1));
    call(delete("/api/v1/problems/" + alpha + "/bookmark"), alice, null)
        .andExpect(status().isNoContent());
    call(get("/api/v1/me/bookmarks"), alice, null).andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void statesMergeBookmarksAndTheStatusWrittenByTheJudgePipeline() throws Exception {
    var u = who(sub());
    UUID id = UUID.fromString(json(call(get("/api/v1/me"), u, null)).get("id").asString());
    UUID betaId =
        jdbc.sql("select id from problems.problems where slug = :s")
            .param("s", beta)
            .query(UUID.class)
            .single();
    UUID alphaId =
        jdbc.sql("select id from problems.problems where slug = :s")
            .param("s", alpha)
            .query(UUID.class)
            .single();
    call(get("/api/v1/me/problems/states"), u, null)
        .andExpect(
            jsonPath("$.states.length()")
                .value(0)); // everything is "not attempted" until Phase 4 writes status

    jdbc.sql(
            "insert into problems.user_problem_status (user_id, problem_id, status, first_solved_at, attempts_count, updated_at) values (:u, :p, 'solved', now(), 2, now())")
        .param("u", id)
        .param("p", alphaId)
        .update();
    jdbc.sql(
            "insert into problems.user_problem_status (user_id, problem_id, status, attempts_count, updated_at) values (:u, :p, 'attempted', 1, now())")
        .param("u", id)
        .param("p", betaId)
        .update();
    call(put("/api/v1/problems/" + beta + "/bookmark"), u, null).andExpect(status().isNoContent());

    JsonNode states = json(call(get("/api/v1/me/problems/states"), u, null));
    var byslug = new java.util.HashMap<String, JsonNode>();
    states.get("states").forEach(s -> byslug.put(s.get("slug").asString(), s));
    assertThat(byslug.get(alpha).get("status").asString()).isEqualTo("solved");
    assertThat(byslug.get(alpha).get("bookmarked").asBoolean()).isFalse();
    assertThat(byslug.get(beta).get("status").asString()).isEqualTo("attempted");
    assertThat(byslug.get(beta).get("bookmarked").asBoolean()).isTrue();

    // "unsolved" random excludes what the caller solved
    for (int i = 0; i < 10; i++) {
      String slug =
          json(
                      call(
                              get(
                                  "/api/v1/problems/random?topic=arrays&difficulty=easy&unsolved=true&pattern="
                                      + patternSlug),
                              u,
                              null)
                          .andExpect(status().isNotFound()))
                  == null
              ? ""
              : "";
      assertThat(slug).isEmpty();
    }
    String other =
        json(call(
                    get(
                        "/api/v1/problems/random?difficulty=medium&unsolved=true&pattern="
                            + patternSlug),
                    u,
                    null)
                .andExpect(status().isOk()))
            .get("slug")
            .asString();
    assertThat(other).isEqualTo(beta);
  }
}
