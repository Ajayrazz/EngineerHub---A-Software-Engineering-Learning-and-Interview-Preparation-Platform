package com.engineerhub.learning.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engineerhub.shared.id.Ids;
import com.engineerhub.testsupport.IntegrationTest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/**
 * Quizzes inside lessons, end to end: authored and published through the real admin API, delivered
 * WITHOUT the answer key, graded on the server against the PUBLISHED version, stored per learner.
 */
@IntegrationTest
class QuizIT {

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired ObjectMapper mapper;
  @Autowired com.engineerhub.catalog.CatalogApi catalog;

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

  private final JwtRequestPostProcessor manager = who(sub(), "CONTENT_MANAGER");

  private ResultActions call(
      MockHttpServletRequestBuilder b, JwtRequestPostProcessor who, Object body) throws Exception {
    MockHttpServletRequestBuilder req = who == null ? b : b.with(who);
    return mvc.perform(
        body == null
            ? req
            : req.contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String s ? s : mapper.writeValueAsString(body)));
  }

  private JsonNode json(ResultActions r) throws Exception {
    return mapper.readTree(r.andReturn().getResponse().getContentAsString());
  }

  // ------------------------------------------------------------------ a published lesson with a
  // quiz

  /** The ids of one quiz: 3 questions (single, multi, true/false) and which options are right. */
  record Fixture(
      String courseId,
      String courseSlug,
      String lessonId,
      String lessonSlug,
      String quizId,
      String q1,
      String q1Right,
      String q1Wrong,
      String q2,
      String q2A,
      String q2B,
      String q2C,
      String q3,
      String q3True,
      String q3False,
      String secret) {}

  private static String id() {
    return UUID.randomUUID().toString();
  }

  private Map<String, Object> option(String id, String text) {
    return new LinkedHashMap<>(Map.of("id", id, "text", text));
  }

  private Map<String, Object> quizBlock(Fixture f, String secret, String q1Correct) {
    Map<String, Object> q1 = new LinkedHashMap<>();
    q1.put("id", f.q1());
    q1.put("type", "single");
    q1.put("prompt", "Which is O(1)?");
    q1.put(
        "options",
        List.of(option(f.q1Right(), "Array index"), option(f.q1Wrong(), "Linear search")));
    q1.put("correct", List.of(q1Correct));
    q1.put("explanation", secret + " one");
    Map<String, Object> q2 = new LinkedHashMap<>();
    q2.put("id", f.q2());
    q2.put("type", "multi");
    q2.put("prompt", "Pick the sorts that are O(n log n) on average");
    q2.put(
        "options",
        List.of(
            option(f.q2A(), "Merge sort"),
            option(f.q2B(), "Bubble sort"),
            option(f.q2C(), "Heap sort")));
    q2.put("correct", List.of(f.q2A(), f.q2C()));
    q2.put("explanation", secret + " two");
    Map<String, Object> q3 = new LinkedHashMap<>();
    q3.put("id", f.q3());
    q3.put("type", "true_false");
    q3.put("prompt", "A stack is LIFO");
    q3.put("options", List.of(option(f.q3True(), "True"), option(f.q3False(), "False")));
    q3.put("correct", List.of(f.q3True()));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("quizId", f.quizId());
    data.put("title", "Check yourself");
    data.put("questions", List.of(q1, q2, q3));
    return Map.of("type", "quiz", "data", data);
  }

  private Fixture newFixture() {
    String tag = Ids.newId().toString().substring(24);
    return new Fixture(
        null,
        "quiz-" + tag,
        null,
        "lesson",
        id(),
        id(),
        id(),
        id(),
        id(),
        id(),
        id(),
        id(),
        id(),
        id(),
        id(),
        "SECRET-EXPLANATION-" + tag);
  }

  /** Creates a course with one lesson holding the quiz; publishes both when asked. */
  private Fixture course(String tier, boolean publish) throws Exception {
    Fixture f0 = newFixture();
    String course =
        json(call(
                    post("/api/v1/admin/courses"),
                    manager,
                    Map.of(
                        "slug",
                        f0.courseSlug(),
                        "title",
                        "Quiz course",
                        "description",
                        "d",
                        "difficulty",
                        "beginner",
                        "accessTier",
                        tier))
                .andExpect(status().isCreated()))
            .get("id")
            .asString();
    String section =
        json(call(
                post("/api/v1/admin/courses/" + course + "/sections"),
                manager,
                Map.of("title", "S")))
            .get("sections")
            .get(0)
            .get("id")
            .asString();
    JsonNode lesson =
        json(
            call(
                    post("/api/v1/admin/courses/" + course + "/lessons"),
                    manager,
                    Map.of("sectionId", section, "slug", f0.lessonSlug(), "title", "Lesson"))
                .andExpect(status().isCreated()));
    Fixture f =
        new Fixture(
            course,
            f0.courseSlug(),
            lesson.get("id").asString(),
            f0.lessonSlug(),
            f0.quizId(),
            f0.q1(),
            f0.q1Right(),
            f0.q1Wrong(),
            f0.q2(),
            f0.q2A(),
            f0.q2B(),
            f0.q2C(),
            f0.q3(),
            f0.q3True(),
            f0.q3False(),
            f0.secret());
    putBlocks(f, f.secret(), f.q1Right(), lesson.get("version").asInt());
    call(
            put("/api/v1/admin/courses/" + course + "/lessons/" + f.lessonId() + "/source"),
            manager,
            Map.of("kind", "original"))
        .andExpect(status().isOk());
    if (publish) {
      publish(f);
    }
    return f;
  }

  private JsonNode putBlocks(Fixture f, String secret, String q1Correct, int version)
      throws Exception {
    return json(
        call(
                put(
                    "/api/v1/admin/courses/"
                        + f.courseId()
                        + "/lessons/"
                        + f.lessonId()
                        + "/blocks"),
                manager,
                Map.of(
                    "expectedVersion",
                    version,
                    "blocks",
                    List.of(
                        Map.of("type", "markdown", "data", Map.of("text", "Read this.")),
                        quizBlock(f, secret, q1Correct))))
            .andExpect(status().isOk()));
  }

  private void publish(Fixture f) throws Exception {
    call(
            post("/api/v1/admin/courses/" + f.courseId() + "/lessons/" + f.lessonId() + "/publish"),
            manager,
            Map.of("originalityConfirmed", true))
        .andExpect(status().isOk());
    call(post("/api/v1/admin/courses/" + f.courseId() + "/publish"), manager, null)
        .andExpect(status().isOk());
  }

  private int version(Fixture f) throws Exception {
    return json(call(
            get("/api/v1/admin/courses/" + f.courseId() + "/lessons/" + f.lessonId()),
            manager,
            null))
        .get("version")
        .asInt();
  }

  private Map<String, Object> answer(String question, String... options) {
    return Map.of("questionId", question, "optionIds", List.of(options));
  }

  private Map<String, Object> allRight(Fixture f) {
    return Map.of(
        "answers",
        List.of(
            answer(f.q1(), f.q1Right()),
            answer(f.q2(), f.q2A(), f.q2C()),
            answer(f.q3(), f.q3True())));
  }

  private String attemptsUrl(Fixture f) {
    return "/api/v1/lessons/" + f.lessonId() + "/quizzes/" + f.quizId() + "/attempts";
  }

  // ------------------------------------------------------------------ what a learner receives

  @Test
  void theReaderNeverReceivesTheAnswerKeyOrTheExplanations() throws Exception {
    Fixture f = course("free", true);
    for (JwtRequestPostProcessor reader : new JwtRequestPostProcessor[] {null, who(sub())}) {
      ResultActions r =
          call(
                  get("/api/v1/courses/" + f.courseSlug() + "/lessons/" + f.lessonSlug()),
                  reader,
                  null)
              .andExpect(status().isOk());
      String raw = r.andReturn().getResponse().getContentAsString();
      assertThat(raw)
          .doesNotContain("correct")
          .doesNotContain("explanation")
          .doesNotContain(f.secret());
      JsonNode quiz = json(r).get("blocks").get(1);
      assertThat(quiz.get("type").asString()).isEqualTo("quiz");
      assertThat(quiz.get("data").get("quizId").asString()).isEqualTo(f.quizId());
      JsonNode q1 = quiz.get("data").get("questions").get(0);
      assertThat(q1.get("prompt").asString()).isEqualTo("Which is O(1)?");
      assertThat(q1.get("options")).hasSize(2);
      assertThat(q1.get("type").asString()).isEqualTo("single");
    }
    // authors, on the other hand, see the key when they edit
    String detail =
        call(
                get("/api/v1/admin/courses/" + f.courseId() + "/lessons/" + f.lessonId()),
                manager,
                null)
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(detail).contains("correct").contains(f.secret());
  }

  // ------------------------------------------------------------------ grading

  @Test
  void anAttemptIsGradedOnTheServerAndRevealsTheKeyAndExplanations() throws Exception {
    Fixture f = course("free", true);
    var u = who(sub());
    JsonNode first =
        json(
            call(
                    post(attemptsUrl(f)),
                    u,
                    Map.of(
                        "answers",
                        List.of(
                            answer(f.q1(), f.q1Wrong()),
                            answer(f.q2(), f.q2A()),
                            answer(f.q3(), f.q3True()))))
                .andExpect(status().isOk()));
    assertThat(first.get("attemptNo").asInt()).isEqualTo(1);
    assertThat(first.get("score").asInt()).isEqualTo(1); // only the true/false one
    assertThat(first.get("total").asInt()).isEqualTo(3);
    JsonNode r1 = first.get("results").get(0);
    assertThat(r1.get("correct").asBoolean()).isFalse();
    assertThat(r1.get("selectedOptionIds").get(0).asString()).isEqualTo(f.q1Wrong());
    assertThat(r1.get("correctOptionIds").get(0).asString()).isEqualTo(f.q1Right());
    assertThat(r1.get("explanation").asString()).isEqualTo(f.secret() + " one");
    assertThat(first.get("results").get(1).get("correctOptionIds"))
        .hasSize(2); // multi: the whole key
    assertThat(first.get("results").get(2).path("explanation").isMissingNode())
        .isTrue(); // none authored

    JsonNode second = json(call(post(attemptsUrl(f)), u, allRight(f)).andExpect(status().isOk()));
    assertThat(second.get("attemptNo").asInt()).isEqualTo(2);
    assertThat(second.get("score").asInt()).isEqualTo(3);

    JsonNode history = json(call(get(attemptsUrl(f)), u, null).andExpect(status().isOk()));
    assertThat(history).hasSize(2);
    assertThat(history.get(0).get("attemptNo").asInt()).isEqualTo(2); // newest first
    assertThat(history.get(0).get("score").asInt()).isEqualTo(3);
    assertThat(history.get(1).get("score").asInt()).isEqualTo(1);
    assertThat(history.toString())
        .doesNotContain(f.secret())
        .doesNotContain("selected"); // scores only

    assertThat(
            jdbc.sql(
                    "select count(*) from shared.outbox where event_type = 'learning.quiz.submitted' and payload::text like :p")
                .param("p", "%" + f.quizId() + "%")
                .query(Long.class)
                .single())
        .isEqualTo(2);
  }

  @Test
  void anUnanswerableOrMalformedAttemptIsRefused() throws Exception {
    Fixture f = course("free", true);
    var u = who(sub());
    call(post(attemptsUrl(f)), u, Map.of("answers", List.of(answer(id(), f.q1Right()))))
        .andExpect(status().isBadRequest()); // unknown question
    call(post(attemptsUrl(f)), u, Map.of("answers", List.of(answer(f.q1(), id()))))
        .andExpect(status().isBadRequest()); // unknown option
    call(
            post(attemptsUrl(f)),
            u,
            Map.of("answers", List.of(answer(f.q1(), f.q1Right(), f.q1Wrong()))))
        .andExpect(status().isBadRequest()); // two for a single
    call(
            post(attemptsUrl(f)),
            u,
            Map.of("answers", List.of(answer(f.q1(), f.q1Right()), answer(f.q1(), f.q1Wrong()))))
        .andExpect(status().isBadRequest()); // twice
    call(post(attemptsUrl(f)), u, Map.of("answers", List.of(answer(f.q2(), f.q2A(), f.q2A()))))
        .andExpect(status().isBadRequest()); // same option twice
    call(post(attemptsUrl(f)), u, Map.of("answers", List.of(Map.of("questionId", f.q1()))))
        .andExpect(status().isBadRequest()); // no optionIds
    call(post(attemptsUrl(f)), u, Map.of("answers", Collections21()))
        .andExpect(status().isBadRequest()); // too many answers
    call(post(attemptsUrl(f)), u, "{}").andExpect(status().isBadRequest());
    call(post(attemptsUrl(f)), u, "{\"answers\":[{\"questionId\":\"nope\",\"optionIds\":[]}]}")
        .andExpect(status().isBadRequest());
    call(post("/api/v1/lessons/not-a-uuid/quizzes/" + f.quizId() + "/attempts"), u, allRight(f))
        .andExpect(status().isBadRequest());
    call(post("/api/v1/lessons/" + f.lessonId() + "/quizzes/" + id() + "/attempts"), u, allRight(f))
        .andExpect(status().isNotFound());
    call(post("/api/v1/lessons/" + id() + "/quizzes/" + f.quizId() + "/attempts"), u, allRight(f))
        .andExpect(status().isNotFound());
    assertThat(
            jdbc.sql("select count(*) from learning.quiz_attempts where quiz_id = :q")
                .param("q", UUID.fromString(f.quizId()))
                .query(Long.class)
                .single())
        .isZero(); // nothing was stored
    // an empty attempt is allowed and simply scores nothing
    assertThat(
            json(call(post(attemptsUrl(f)), u, Map.of("answers", List.of()))
                    .andExpect(status().isOk()))
                .get("score")
                .asInt())
        .isZero();
  }

  private List<Object> Collections21() {
    List<Object> many = new ArrayList<>();
    for (int i = 0; i < 21; i++) many.add(answer(id(), id()));
    return many;
  }

  // ------------------------------------------------------------------ whose attempts, and when

  @Test
  void attemptsBelongToTheirOwnerAndAnonymousCallersAreRefused() throws Exception {
    Fixture f = course("free", true);
    var a = who(sub());
    var b = who(sub());
    call(post(attemptsUrl(f)), null, allRight(f)).andExpect(status().isUnauthorized());
    call(get(attemptsUrl(f)), null, null).andExpect(status().isUnauthorized());
    call(post(attemptsUrl(f)), a, allRight(f)).andExpect(status().isOk());
    call(post(attemptsUrl(f)), a, allRight(f)).andExpect(status().isOk());
    assertThat(json(call(get(attemptsUrl(f)), b, null)).size()).isZero(); // B sees nothing of A's
    assertThat(json(call(post(attemptsUrl(f)), b, allRight(f))).get("attemptNo").asInt())
        .isEqualTo(1); // numbering is per learner
    assertThat(json(call(get(attemptsUrl(f)), a, null)).size()).isEqualTo(2);
    // a user id in the body changes nothing: ownership is the token
    JsonNode spoof =
        json(call(post(attemptsUrl(f)), b, "{\"userId\":\"" + id() + "\",\"answers\":[]}"));
    assertThat(spoof.get("attemptNo").asInt()).isEqualTo(2);
  }

  @Test
  void aPremiumLessonsQuizNeedsTheSameEntitlementAsReadingIt() throws Exception {
    Fixture f = course("premium", true);
    var learner = who(sub());
    call(post(attemptsUrl(f)), learner, allRight(f)).andExpect(status().isForbidden());
    call(get(attemptsUrl(f)), learner, null).andExpect(status().isForbidden());
    assertThat(
            jdbc.sql("select count(*) from learning.quiz_attempts where quiz_id = :q")
                .param("q", UUID.fromString(f.quizId()))
                .query(Long.class)
                .single())
        .isZero();
    call(post(attemptsUrl(f)), manager, allRight(f))
        .andExpect(status().isOk()); // staff keep access through their role
  }

  @Test
  void onlyTheKeyOfThePublishedVersionGradesAndUnpublishedQuizzesAreNotFound() throws Exception {
    Fixture f = course("free", false);
    var u = who(sub());
    call(post(attemptsUrl(f)), u, allRight(f))
        .andExpect(status().isNotFound()); // a draft is not gradable
    publish(f);
    assertThat(json(call(post(attemptsUrl(f)), u, allRight(f))).get("score").asInt()).isEqualTo(3);

    // the author edits the DRAFT so that the other option is "correct": learners are still graded
    // on what was published
    putBlocks(f, "SECRET-NEW", f.q1Wrong(), version(f)).get("version");
    JsonNode still = json(call(post(attemptsUrl(f)), u, allRight(f)));
    assertThat(still.get("score").asInt()).isEqualTo(3);
    assertThat(still.get("results").get(0).get("explanation").asString())
        .isEqualTo(f.secret() + " one");

    // publishing the edit changes grading from then on
    call(
            post("/api/v1/admin/courses/" + f.courseId() + "/lessons/" + f.lessonId() + "/publish"),
            manager,
            Map.of("originalityConfirmed", true))
        .andExpect(status().isOk());
    JsonNode after = json(call(post(attemptsUrl(f)), u, allRight(f)));
    assertThat(after.get("score").asInt()).isEqualTo(2); // question 1 is now wrong
    assertThat(after.get("results").get(0).get("explanation").asString())
        .isEqualTo("SECRET-NEW one");

    call(
            post(
                "/api/v1/admin/courses/"
                    + f.courseId()
                    + "/lessons/"
                    + f.lessonId()
                    + "/unpublish"),
            manager,
            null)
        .andExpect(status().isOk());
    call(post(attemptsUrl(f)), u, allRight(f)).andExpect(status().isNotFound());
    call(get(attemptsUrl(f)), u, null).andExpect(status().isNotFound());
  }

  @Test
  void aLearnerCannotKeepGrindingForeverAndAttemptNumbersStayUnique() throws Exception {
    Fixture f = course("free", true);
    var u = who(sub());
    String userId = json(call(get("/api/v1/me"), u, null)).get("id").asString();
    for (int n = 1; n <= 100; n++) {
      jdbc.sql(
              "insert into learning.quiz_attempts (id, user_id, lesson_id, course_id, quiz_id, attempt_no, score, total, answers, created_at) values (:id, :u, :l, :c, :q, :n, 0, 3, '[]'::jsonb, now())")
          .param("id", Ids.newId())
          .param("u", UUID.fromString(userId))
          .param("l", UUID.fromString(f.lessonId()))
          .param("c", UUID.fromString(f.courseId()))
          .param("q", UUID.fromString(f.quizId()))
          .param("n", n)
          .update();
    }
    call(post(attemptsUrl(f)), u, allRight(f)).andExpect(status().isTooManyRequests());
    assertThat(json(call(get(attemptsUrl(f)), u, null))).hasSize(20); // history is bounded too
  }

  @Test
  void theCatalogHandsOutAnAnswerKeyOnlyForPublishedLessons() throws Exception {
    Fixture f = course("free", false);
    UUID lesson = UUID.fromString(f.lessonId());
    UUID quiz = UUID.fromString(f.quizId());
    assertThat(catalog.publishedQuiz(lesson, quiz)).isEmpty(); // a draft has no public key
    publish(f);
    var key = catalog.publishedQuiz(lesson, quiz).orElseThrow();
    assertThat(key.questions()).hasSize(3);
    assertThat(key.questions().get(0).correct()).containsExactly(UUID.fromString(f.q1Right()));
    assertThat(key.questions().get(1).correct()).hasSize(2);
    assertThat(catalog.publishedQuiz(lesson, UUID.randomUUID())).isEmpty(); // no such quiz
    assertThat(catalog.publishedQuiz(UUID.randomUUID(), quiz)).isEmpty(); // no such lesson
    call(
            post(
                "/api/v1/admin/courses/"
                    + f.courseId()
                    + "/lessons/"
                    + f.lessonId()
                    + "/unpublish"),
            manager,
            null)
        .andExpect(status().isOk());
    assertThat(catalog.publishedQuiz(lesson, quiz)).isEmpty(); // unpublished again
    // a published lesson of an UNPUBLISHED course is not public either
    call(
            post("/api/v1/admin/courses/" + f.courseId() + "/lessons/" + f.lessonId() + "/publish"),
            manager,
            Map.of("originalityConfirmed", true))
        .andExpect(status().isOk());
    assertThat(catalog.publishedQuiz(lesson, quiz)).isPresent();
    call(post("/api/v1/admin/courses/" + f.courseId() + "/unpublish"), manager, null)
        .andExpect(status().isOk());
    assertThat(catalog.publishedQuiz(lesson, quiz)).isEmpty();
    call(post(attemptsUrl(f)), who(sub()), allRight(f)).andExpect(status().isNotFound());
  }
}
