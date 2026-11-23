package com.engineerhub.learning.internal;

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
import java.util.UUID;
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

/** Enrollment and lesson progress: ownership, gating, completion arithmetic and events. */
@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LearningIT {

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

  private String courseId;
  private String draftCourseId;
  private String lessonA;
  private String lessonB;
  private String lessonPremium;
  private String draftLesson;

  private String lesson(String course, String section, String slug, String tier) throws Exception {
    String id =
        json(call(
                post("/api/v1/admin/courses/" + course + "/lessons"),
                manager,
                "{\"sectionId\":\"%s\",\"slug\":\"%s\",\"title\":\"Lesson %s\",\"accessTier\":\"%s\"}"
                    .formatted(section, slug, slug, tier)))
            .get("id")
            .asString();
    call(
            put("/api/v1/admin/courses/" + course + "/lessons/" + id + "/blocks"),
            manager,
            "{\"expectedVersion\":0,\"blocks\":[{\"type\":\"markdown\",\"data\":{\"text\":\"body %s\"}}]}"
                .formatted(slug))
        .andExpect(status().isOk());
    call(
            put("/api/v1/admin/courses/" + course + "/lessons/" + id + "/source"),
            manager,
            "{\"kind\":\"original\"}")
        .andExpect(status().isOk());
    return id;
  }

  private void publish(String course, String lesson) throws Exception {
    call(
            post("/api/v1/admin/courses/" + course + "/lessons/" + lesson + "/publish"),
            manager,
            "{\"originalityConfirmed\":true}")
        .andExpect(status().isOk());
  }

  @BeforeAll
  void fixture() throws Exception {
    String tag = Ids.newId().toString().substring(24);
    courseId =
        json(call(
                post("/api/v1/admin/courses"),
                manager,
                "{\"slug\":\"learn-%s\",\"title\":\"Learn\",\"description\":\"d\",\"difficulty\":\"beginner\",\"accessTier\":\"premium\"}"
                    .formatted(tag)))
            .get("id")
            .asString();
    String section =
        json(call(
                post("/api/v1/admin/courses/" + courseId + "/sections"),
                manager,
                "{\"title\":\"S\"}"))
            .get("sections")
            .get(0)
            .get("id")
            .asString();
    lessonA = lesson(courseId, section, "a", "free");
    lessonB = lesson(courseId, section, "b", "free");
    lessonPremium = lesson(courseId, section, "c", "premium");
    draftLesson = lesson(courseId, section, "d", "free"); // never published
    publish(courseId, lessonA);
    publish(courseId, lessonB);
    publish(courseId, lessonPremium);
    call(post("/api/v1/admin/courses/" + courseId + "/publish"), manager, null)
        .andExpect(status().isOk());

    draftCourseId =
        json(call(
                post("/api/v1/admin/courses"),
                manager,
                "{\"slug\":\"draft-%s\",\"title\":\"D\",\"description\":\"d\",\"difficulty\":\"beginner\",\"accessTier\":\"free\"}"
                    .formatted(tag)))
            .get("id")
            .asString();
  }

  private ResultActions progress(
      JwtRequestPostProcessor u, String lesson, String status, Integer pct) throws Exception {
    return call(
        put("/api/v1/lessons/" + lesson + "/progress"),
        u,
        "{\"status\":\"%s\"%s}".formatted(status, pct == null ? "" : ",\"scrollPct\":" + pct));
  }

  private long completionEvents(String user, String lesson) {
    return jdbc.sql(
            "select count(*) from shared.outbox where event_type = 'learning.lesson.completed' and aggregate_id = :u and payload::text like :p")
        .param("u", user)
        .param("p", "%" + lesson + "%")
        .query(Long.class)
        .single();
  }

  // ------------------------------------------------------------------ authentication & visibility

  @Test
  void everythingNeedsLogin() throws Exception {
    call(post("/api/v1/courses/" + courseId + "/enroll"), null, null)
        .andExpect(status().isUnauthorized());
    call(get("/api/v1/me/enrollments"), null, null).andExpect(status().isUnauthorized());
    call(get("/api/v1/me/courses/" + courseId + "/progress"), null, null)
        .andExpect(status().isUnauthorized());
    call(put("/api/v1/lessons/" + lessonA + "/progress"), null, "{\"status\":\"completed\"}")
        .andExpect(status().isUnauthorized());
  }

  @Test
  void onlyPublishedCoursesAndLessonsCanBeUsed() throws Exception {
    var u = who(sub());
    call(post("/api/v1/courses/" + draftCourseId + "/enroll"), u, null)
        .andExpect(status().isNotFound());
    call(post("/api/v1/courses/" + UUID.randomUUID() + "/enroll"), u, null)
        .andExpect(status().isNotFound());
    progress(u, draftLesson, "completed", null)
        .andExpect(status().isNotFound()); // lesson never published
    progress(u, UUID.randomUUID().toString(), "completed", null).andExpect(status().isNotFound());
    call(get("/api/v1/me/courses/" + draftCourseId + "/progress"), u, null)
        .andExpect(status().isNotFound());
  }

  // ------------------------------------------------------------------ enrollment

  @Test
  void enrollingIsIdempotent() throws Exception {
    var u = who(sub());
    call(get("/api/v1/me/courses/" + courseId + "/progress"), u, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enrolled").value(false))
        .andExpect(jsonPath("$.totalLessons").value(3));
    call(post("/api/v1/courses/" + courseId + "/enroll"), u, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enrolled").value(true))
        .andExpect(jsonPath("$.resume.lessonSlug").value("a"));
    call(post("/api/v1/courses/" + courseId + "/enroll"), u, null).andExpect(status().isOk());
    assertThat(
            jdbc.sql("select count(*) from learning.enrollments where course_id = :c")
                .param("c", UUID.fromString(courseId))
                .query(Long.class)
                .single())
        .isGreaterThanOrEqualTo(1);
    call(get("/api/v1/me/enrollments"), u, null)
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].courseId").value(courseId))
        .andExpect(jsonPath("$[0].percent").value(0));
  }

  // ------------------------------------------------------------------ progress arithmetic

  @Test
  void progressCompletionResumeAndEvents() throws Exception {
    var u = who(sub());
    progress(u, lessonA, "in_progress", 40)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enrolled").value(true))
        .andExpect(jsonPath("$.completedLessons").value(0))
        .andExpect(jsonPath("$.resume.lessonSlug").value("a"));
    progress(u, lessonA, "completed", null)
        .andExpect(jsonPath("$.completedLessons").value(1))
        .andExpect(jsonPath("$.percent").value(33))
        .andExpect(jsonPath("$.resume.lessonSlug").value("b")); // next unfinished
    progress(u, lessonA, "completed", null)
        .andExpect(jsonPath("$.completedLessons").value(1)); // idempotent
    progress(u, lessonB, "in_progress", 10).andExpect(jsonPath("$.resume.lessonSlug").value("b"));

    // the premium lesson is not open to a learner, so the course cannot be finished by them yet
    progress(u, lessonPremium, "completed", null)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ENTITLEMENT_REQUIRED"));
    progress(u, lessonB, "completed", null)
        .andExpect(jsonPath("$.completedLessons").value(2))
        .andExpect(jsonPath("$.completed").value(false));

    // un-marking goes back
    progress(u, lessonA, "in_progress", 0).andExpect(jsonPath("$.completedLessons").value(1));
    progress(u, lessonA, "completed", null).andExpect(jsonPath("$.completedLessons").value(2));

    String me = json(call(get("/api/v1/me"), u, null)).get("id").asString();
    assertThat(completionEvents(me, lessonA))
        .as("A: first completion + completion after un-marking, none for the repeat")
        .isEqualTo(2);
    assertThat(completionEvents(me, lessonB)).isEqualTo(1);
  }

  @Test
  void finishingEveryPublishedLessonCompletesTheCourseOnce() throws Exception {
    var staff = who(sub(), "ADMIN"); // entitled to the premium lesson by the stub
    progress(staff, lessonA, "completed", null);
    progress(staff, lessonB, "completed", null);
    progress(staff, lessonPremium, "completed", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completed").value(true))
        .andExpect(jsonPath("$.percent").value(100));
    progress(staff, lessonPremium, "completed", null)
        .andExpect(jsonPath("$.completed").value(true));
    String me = json(call(get("/api/v1/me"), staff, null)).get("id").asString();
    assertThat(
            jdbc.sql(
                    "select count(*) from shared.outbox where event_type = 'learning.course.completed' and aggregate_id = :u")
                .param("u", me)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    call(get("/api/v1/me/enrollments"), staff, null)
        .andExpect(jsonPath("$[0].completed").value(true));
  }

  @Test
  void unpublishingALessonStopsCountingIt() throws Exception {
    // fresh course so other tests are unaffected
    String tag = Ids.newId().toString().substring(24);
    String c =
        json(call(
                post("/api/v1/admin/courses"),
                manager,
                "{\"slug\":\"shrink-%s\",\"title\":\"T\",\"description\":\"d\",\"difficulty\":\"beginner\",\"accessTier\":\"free\"}"
                    .formatted(tag)))
            .get("id")
            .asString();
    String s =
        json(call(post("/api/v1/admin/courses/" + c + "/sections"), manager, "{\"title\":\"S\"}"))
            .get("sections")
            .get(0)
            .get("id")
            .asString();
    String one = lesson(c, s, "one", "free");
    String two = lesson(c, s, "two", "free");
    publish(c, one);
    publish(c, two);
    call(post("/api/v1/admin/courses/" + c + "/publish"), manager, null).andExpect(status().isOk());

    var u = who(sub());
    progress(u, one, "completed", null).andExpect(jsonPath("$.percent").value(50));
    progress(u, two, "completed", null).andExpect(jsonPath("$.percent").value(100));
    call(post("/api/v1/admin/courses/" + c + "/lessons/" + two + "/unpublish"), manager, null)
        .andExpect(status().isOk());
    // the lesson completed earlier no longer exists for readers, so it no longer counts
    call(get("/api/v1/me/courses/" + c + "/progress"), u, null)
        .andExpect(jsonPath("$.totalLessons").value(1))
        .andExpect(jsonPath("$.completedLessons").value(1))
        .andExpect(jsonPath("$.percent").value(100));
    // unpublishing the course removes it from "my courses"
    call(post("/api/v1/admin/courses/" + c + "/unpublish"), manager, null)
        .andExpect(status().isOk());
    call(get("/api/v1/me/enrollments"), u, null).andExpect(jsonPath("$.length()").value(0));
    call(get("/api/v1/me/courses/" + c + "/progress"), u, null).andExpect(status().isNotFound());
  }

  // ------------------------------------------------------------------ ownership & validation

  @Test
  void progressBelongsToTheCallerOnly() throws Exception {
    var alice = who(sub());
    var bob = who(sub());
    progress(alice, lessonA, "completed", null).andExpect(jsonPath("$.completedLessons").value(1));
    call(get("/api/v1/me/courses/" + courseId + "/progress"), bob, null)
        .andExpect(jsonPath("$.completedLessons").value(0))
        .andExpect(jsonPath("$.enrolled").value(false));
    call(get("/api/v1/me/enrollments"), bob, null).andExpect(jsonPath("$.length()").value(0));
    // a user id in the body or query is ignored: there is no way to name another user
    call(
            put("/api/v1/lessons/" + lessonB + "/progress"),
            bob,
            "{\"status\":\"completed\",\"userId\":\"" + UUID.randomUUID() + "\"}")
        .andExpect(status().isOk());
    call(get("/api/v1/me/courses/" + courseId + "/progress"), alice, null)
        .andExpect(jsonPath("$.completedLessons").value(1));
  }

  @Test
  void invalidInputIsRejected() throws Exception {
    var u = who(sub());
    progress(u, lessonA, "done", null).andExpect(status().isBadRequest());
    progress(u, lessonA, "in_progress", 101).andExpect(status().isBadRequest());
    progress(u, lessonA, "in_progress", -1).andExpect(status().isBadRequest());
    call(put("/api/v1/lessons/not-a-uuid/progress"), u, "{\"status\":\"completed\"}")
        .andExpect(status().isBadRequest());
    call(put("/api/v1/lessons/" + lessonA + "/progress"), u, "{}")
        .andExpect(status().isBadRequest());
    call(post("/api/v1/courses/not-a-uuid/enroll"), u, null).andExpect(status().isBadRequest());
  }

  @Test
  void aSuspendedAccountCannotRecordProgress() throws Exception {
    String s = sub();
    var u = who(s);
    String id = json(call(get("/api/v1/me"), u, null)).get("id").asString();
    call(
            put("/api/v1/admin/users/" + id + "/status"),
            who(sub(), "SUPER_ADMIN"),
            "{\"status\":\"suspended\",\"reason\":\"learning test\"}")
        .andExpect(status().isOk());
    progress(u, lessonA, "completed", null).andExpect(status().isForbidden());
  }

  @Test
  void courseOutlinePublishedLessonDtoCarriesTheLessonId() throws Exception {
    String slug =
        json(call(get("/api/v1/admin/courses/" + courseId), manager, null)).get("slug").asString();
    call(get("/api/v1/courses/" + slug + "/lessons/a"), null, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(lessonA));
    call(
            patch("/api/v1/admin/courses/" + courseId),
            manager,
            "{\"title\":\"Learn\",\"expectedVersion\":-5}")
        .andExpect(status().isConflict());
  }
}
