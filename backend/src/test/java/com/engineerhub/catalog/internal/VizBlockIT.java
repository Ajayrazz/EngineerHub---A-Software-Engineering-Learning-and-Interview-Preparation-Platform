package com.engineerhub.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engineerhub.shared.id.Ids;
import com.engineerhub.testsupport.IntegrationTest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Visualization blocks through the real admin API and the public reader. */
@IntegrationTest
class VizBlockIT {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;

  private final JwtRequestPostProcessor manager =
      jwt()
          .jwt(
              j ->
                  j.subject("kc-" + Ids.newId())
                      .claim(
                          "preferred_username",
                          "vizmgr-" + java.util.UUID.randomUUID().toString().substring(0, 8))
                      .claim(
                          "email",
                          java.util.UUID.randomUUID().toString().substring(0, 8) + "@example.test"))
          .authorities(
              Arrays.stream(new String[] {"CONTENT_MANAGER"})
                  .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                  .toArray(SimpleGrantedAuthority[]::new));

  private ResultActions call(
      MockHttpServletRequestBuilder b, JwtRequestPostProcessor who, Object body) throws Exception {
    MockHttpServletRequestBuilder req = who == null ? b : b.with(who);
    return mvc.perform(
        body == null
            ? req
            : req.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
  }

  private JsonNode json(ResultActions r) throws Exception {
    return mapper.readTree(r.andReturn().getResponse().getContentAsString());
  }

  private static Map<String, Object> viz(String algorithm, String input, String param) {
    return param == null
        ? Map.of("algorithm", algorithm, "input", input)
        : Map.of("algorithm", algorithm, "input", input, "param", param);
  }

  private record Lesson(String courseId, String lessonId, String courseSlug, int version) {}

  private Lesson lesson() throws Exception {
    String slug = "viz-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    String course =
        json(call(
                    post("/api/v1/admin/courses"),
                    manager,
                    Map.of(
                        "slug",
                        slug,
                        "title",
                        "Viz",
                        "description",
                        "d",
                        "difficulty",
                        "beginner",
                        "accessTier",
                        "free"))
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
    JsonNode l =
        json(
            call(
                    post("/api/v1/admin/courses/" + course + "/lessons"),
                    manager,
                    Map.of("sectionId", section, "slug", "l", "title", "L"))
                .andExpect(status().isCreated()));
    return new Lesson(course, l.get("id").asString(), slug, l.get("version").asInt());
  }

  private ResultActions save(Lesson l, int version, Object... blocks) throws Exception {
    return call(
        put("/api/v1/admin/courses/" + l.courseId() + "/lessons/" + l.lessonId() + "/blocks"),
        manager,
        Map.of("expectedVersion", version, "blocks", List.of(blocks)));
  }

  private void publish(Lesson l) throws Exception {
    call(
            put("/api/v1/admin/courses/" + l.courseId() + "/lessons/" + l.lessonId() + "/source"),
            manager,
            Map.of("kind", "original"))
        .andExpect(status().isOk());
    call(
            post("/api/v1/admin/courses/" + l.courseId() + "/lessons/" + l.lessonId() + "/publish"),
            manager,
            Map.of("originalityConfirmed", true))
        .andExpect(status().isOk());
    call(post("/api/v1/admin/courses/" + l.courseId() + "/publish"), manager, null)
        .andExpect(status().isOk());
  }

  @Test
  void aVisualizationIsAuthoredPublishedAndDeliveredAsExactlyItsAlgorithmAndInput()
      throws Exception {
    Lesson l = lesson();
    JsonNode saved =
        json(
            save(
                    l,
                    l.version(),
                    Map.of("type", "viz", "data", viz("binary-search", " 1, 3, 5, 7 ", " 5 ")))
                .andExpect(status().isOk()));
    JsonNode block = saved.get("blocks").get(0);
    assertThat(block.get("type").asString()).isEqualTo("viz");
    assertThat(block.get("data").get("input").asString()).isEqualTo("1, 3, 5, 7"); // stored trimmed
    assertThat(block.get("data").get("param").asString()).isEqualTo("5");
    publish(l);
    for (JwtRequestPostProcessor reader : new JwtRequestPostProcessor[] {null, manager}) {
      JsonNode pub =
          json(call(get("/api/v1/courses/" + l.courseSlug() + "/lessons/l"), reader, null)
                  .andExpect(status().isOk()))
              .get("blocks")
              .get(0);
      assertThat(pub.get("type").asString()).isEqualTo("viz");
      assertThat(pub.get("data").size()).isEqualTo(3); // algorithm, input, param: nothing else
      assertThat(pub.get("data").get("algorithm").asString()).isEqualTo("binary-search");
    }
  }

  @Test
  void aVisualizationMayShareALessonWithOtherBlocksAndKeepsItsPosition() throws Exception {
    Lesson l = lesson();
    JsonNode saved =
        json(
            save(
                    l,
                    l.version(),
                    Map.of("type", "markdown", "data", Map.of("text", "Before")),
                    Map.of("type", "viz", "data", viz("bubble-sort", "3, 1, 2", null)),
                    Map.of("type", "markdown", "data", Map.of("text", "After")))
                .andExpect(status().isOk()));
    assertThat(saved.get("blocks").get(1).get("type").asString()).isEqualTo("viz");
    assertThat(saved.get("blocks").get(2).get("data").get("text").asString()).isEqualTo("After");
  }

  @Test
  void hostileOrMalformedVisualizationsAreRefusedWithAProblemAndNothingIsStored() throws Exception {
    Lesson l = lesson();
    for (Map<String, Object> data :
        List.of(
            viz("quick-sort", "3, 1, 2", null),
            viz("bubble-sort", "<script>alert(1)</script>", null),
            viz("bubble-sort", "3, 1, 2; DROP TABLE x", null),
            viz("binary-search", "1, 2, 3", null),
            viz("bubble-sort", "3, 1, 2", "5"),
            viz("bfs", "0-9", "0"),
            Map.<String, Object>of(
                "algorithm", "bubble-sort", "input", "3, 1, 2", "html", "<b>x</b>"))) {
      JsonNode problem =
          json(
              save(l, l.version(), Map.of("type", "viz", "data", data))
                  .andExpect(status().isBadRequest()));
      assertThat(problem.get("code").asString()).isEqualTo("VALIDATION_FAILED");
      assertThat(problem.toString()).doesNotContain("Exception").doesNotContain("at com.");
    }
    JsonNode stored =
        json(
            call(
                get("/api/v1/admin/courses/" + l.courseId() + "/lessons/" + l.lessonId()),
                manager,
                null));
    assertThat(stored.get("blocks")).isEmpty();
  }

  @Test
  void aDraftEditNeverChangesWhatReadersSee() throws Exception {
    Lesson l = lesson();
    JsonNode v1 =
        json(
            save(l, l.version(), Map.of("type", "viz", "data", viz("bubble-sort", "3, 1, 2", null)))
                .andExpect(status().isOk()));
    publish(l);
    json(
        save(
                l,
                v1.get("version").asInt(),
                Map.of("type", "viz", "data", viz("selection-sort", "9, 8, 7", null)))
            .andExpect(status().isOk()));
    JsonNode pub =
        json(call(get("/api/v1/courses/" + l.courseSlug() + "/lessons/l"), null, null)
                .andExpect(status().isOk()))
            .get("blocks")
            .get(0);
    assertThat(pub.get("data").get("algorithm").asString())
        .isEqualTo("bubble-sort"); // still the published one
  }
}
