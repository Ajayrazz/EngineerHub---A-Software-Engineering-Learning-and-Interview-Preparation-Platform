package com.engineerhub.analytics.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engineerhub.shared.id.Ids;
import com.engineerhub.testsupport.IntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.UUID;
import org.awaitility.Awaitility;
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
 * Activity statistics: the real event path (API -> outbox -> RabbitMQ -> consumer) and the
 * day/streak rules.
 */
@IntegrationTest
class AnalyticsIT {

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired ObjectMapper mapper;
  @Autowired AnalyticsService analytics;

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

  private UUID userId(JwtRequestPostProcessor u) throws Exception {
    return UUID.fromString(json(call(get("/api/v1/me"), u, null)).get("id").asString());
  }

  private JsonNode dashboard(JwtRequestPostProcessor u) throws Exception {
    return json(call(get("/api/v1/me/dashboard"), u, null).andExpect(status().isOk()));
  }

  private void awaitLessons(JwtRequestPostProcessor u, int expected) {
    Awaitility.await()
        .atMost(java.time.Duration.ofSeconds(20))
        .pollInterval(java.time.Duration.ofMillis(200))
        .until(() -> dashboard(u).get("lessonsCompleted").asInt() == expected);
  }

  /** A published course with two free lessons; returns {courseId, lessonA, lessonB}. */
  private String[] course() throws Exception {
    String tag = Ids.newId().toString().substring(24);
    String c =
        json(call(
                post("/api/v1/admin/courses"),
                manager,
                "{\"slug\":\"an-%s\",\"title\":\"A\",\"description\":\"d\",\"difficulty\":\"beginner\",\"accessTier\":\"free\"}"
                    .formatted(tag)))
            .get("id")
            .asString();
    String s =
        json(call(post("/api/v1/admin/courses/" + c + "/sections"), manager, "{\"title\":\"S\"}"))
            .get("sections")
            .get(0)
            .get("id")
            .asString();
    String[] lessons = new String[2];
    for (int i = 0; i < 2; i++) {
      String id =
          json(call(
                  post("/api/v1/admin/courses/" + c + "/lessons"),
                  manager,
                  "{\"sectionId\":\"%s\",\"slug\":\"l%d\",\"title\":\"L\"}".formatted(s, i)))
              .get("id")
              .asString();
      call(
              put("/api/v1/admin/courses/" + c + "/lessons/" + id + "/blocks"),
              manager,
              "{\"expectedVersion\":0,\"blocks\":[{\"type\":\"markdown\",\"data\":{\"text\":\"b\"}}]}")
          .andExpect(status().isOk());
      call(
              put("/api/v1/admin/courses/" + c + "/lessons/" + id + "/source"),
              manager,
              "{\"kind\":\"original\"}")
          .andExpect(status().isOk());
      call(
              post("/api/v1/admin/courses/" + c + "/lessons/" + id + "/publish"),
              manager,
              "{\"originalityConfirmed\":true}")
          .andExpect(status().isOk());
      lessons[i] = id;
    }
    call(post("/api/v1/admin/courses/" + c + "/publish"), manager, null).andExpect(status().isOk());
    return new String[] {c, lessons[0], lessons[1]};
  }

  private ResultActions progress(JwtRequestPostProcessor u, String lesson, String status)
      throws Exception {
    return call(
        put("/api/v1/lessons/" + lesson + "/progress"), u, "{\"status\":\"%s\"}".formatted(status));
  }

  // ------------------------------------------------------------------ end to end through RabbitMQ

  @Test
  void completingLessonsThroughTheApiReachesTheDashboardViaTheEventPipeline() throws Exception {
    String[] c = course();
    var u = who(sub());
    JsonNode empty = dashboard(u);
    assertThat(empty.get("lessonsCompleted").asInt()).isZero();
    assertThat(empty.get("streak").get("current").asInt()).isZero();
    assertThat(empty.get("streak").get("activeToday").asBoolean()).isFalse();

    progress(u, c[1], "completed").andExpect(status().isOk());
    awaitLessons(u, 1);
    JsonNode d = dashboard(u);
    assertThat(d.get("streak").get("current").asInt()).isEqualTo(1);
    assertThat(d.get("streak").get("activeToday").asBoolean()).isTrue();
    assertThat(d.get("coursesCompleted").asInt()).isZero();
    assertThat(d.get("activity").get(0).get("lessonsCompleted").asInt()).isEqualTo(1);

    // un-marking and re-completing the same lesson must not inflate anything
    progress(u, c[1], "in_progress").andExpect(status().isOk());
    progress(u, c[1], "completed").andExpect(status().isOk());
    progress(u, c[2], "completed").andExpect(status().isOk()); // second lesson finishes the course
    awaitLessons(u, 2);
    Awaitility.await()
        .atMost(java.time.Duration.ofSeconds(20))
        .until(() -> dashboard(u).get("coursesCompleted").asInt() == 1);
    JsonNode after = dashboard(u);
    assertThat(after.get("lessonsCompleted").asInt()).isEqualTo(2);
    assertThat(after.get("streak").get("current").asInt())
        .as("two lessons on one day are one streak day")
        .isEqualTo(1);
    assertThat(after.get("activity").size()).isEqualTo(1);
    assertThat(after.get("activity").get(0).get("lessonsCompleted").asInt()).isEqualTo(2);
  }

  @Test
  void theDashboardNeedsLoginAndIsPerUser() throws Exception {
    call(get("/api/v1/me/dashboard"), null, null).andExpect(status().isUnauthorized());
    String[] c = course();
    var alice = who(sub());
    var bob = who(sub());
    progress(alice, c[1], "completed").andExpect(status().isOk());
    awaitLessons(alice, 1);
    assertThat(dashboard(bob).get("lessonsCompleted").asInt()).isZero();
    assertThat(dashboard(bob).get("streak").get("current").asInt()).isZero();
  }

  // ------------------------------------------------------------------ rules through the service

  @Test
  void aLessonCountsOnceNoMatterHowOftenItIsReported() throws Exception {
    var u = who(sub());
    UUID id = userId(u);
    UUID lesson = UUID.randomUUID();
    UUID course = UUID.randomUUID();
    Instant at = Instant.parse("2026-03-10T10:00:00Z");
    assertThat(analytics.onLessonCompleted(id, lesson, course, at)).isTrue();
    assertThat(analytics.onLessonCompleted(id, lesson, course, at.plusSeconds(60))).isFalse();
    assertThat(analytics.onLessonCompleted(id, lesson, course, at.plusSeconds(86_400))).isFalse();
    assertThat(
            jdbc.sql("select lessons_completed from analytics.daily_user_stats where user_id = :u")
                .param("u", id)
                .query(Integer.class)
                .single())
        .isEqualTo(1);
    assertThat(analytics.onCourseCompleted(id, course, at)).isTrue();
    assertThat(analytics.onCourseCompleted(id, course, at.plusSeconds(5))).isFalse();
  }

  @Test
  void streaksFollowConsecutiveDaysAndRestartAfterAGap() throws Exception {
    var u = who(sub());
    UUID id = userId(u);
    UUID course = UUID.randomUUID();
    Instant day1 = Instant.parse("2026-03-10T10:00:00Z");
    for (int d = 0; d < 3; d++) {
      analytics.onLessonCompleted(id, UUID.randomUUID(), course, day1.plusSeconds(86_400L * d));
    }
    assertThat(streak(id)).containsExactly(3, 3);
    analytics.onLessonCompleted(
        id, UUID.randomUUID(), course, day1.plusSeconds(86_400L * 5)); // skipped a day
    assertThat(streak(id)).containsExactly(1, 3);
    analytics.onLessonCompleted(
        id,
        UUID.randomUUID(),
        course,
        day1); // late event for an old day: counted, streak untouched
    assertThat(streak(id)).containsExactly(1, 3);
    assertThat(
            jdbc.sql("select count(*) from analytics.daily_user_stats where user_id = :u")
                .param("u", id)
                .query(Long.class)
                .single())
        .isEqualTo(4);
  }

  private int[] streak(UUID id) {
    return jdbc.sql(
            "select current_streak, longest_streak from analytics.user_streaks where user_id = :u")
        .param("u", id)
        .query((rs, i) -> new int[] {rs.getInt(1), rs.getInt(2)})
        .single();
  }

  @Test
  void theDayBoundaryIsTheUsersTimezone() throws Exception {
    var u = who(sub());
    UUID id = userId(u);
    call(put("/api/v1/me/preferences"), u, "{\"timezone\":\"Pacific/Auckland\",\"locale\":\"en\"}")
        .andExpect(status().isOk());
    // 2026-01-01 12:30 UTC is already January 2nd, 01:30 in Auckland (NZDT, +13)
    analytics.onLessonCompleted(
        id, UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-01-01T12:30:00Z"));
    assertThat(
            jdbc.sql("select day from analytics.daily_user_stats where user_id = :u")
                .param("u", id)
                .query(java.sql.Date.class)
                .single()
                .toLocalDate())
        .isEqualTo(LocalDate.of(2026, 1, 2));
    assertThat(dashboard(u).get("timezone").asString()).isEqualTo("Pacific/Auckland");
    LocalDate today = LocalDate.now(ZoneId.of("Pacific/Auckland"));
    assertThat(LocalDate.parse(dashboard(u).get("today").asString()))
        .isBetween(today.minusDays(1), today.plusDays(1));
  }
}
