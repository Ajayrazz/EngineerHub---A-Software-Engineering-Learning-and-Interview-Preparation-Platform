package com.engineerhub.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * Authoring workflow, publish gates and authorization of the catalog admin API (real PostgreSQL).
 */
@IntegrationTest
class CatalogAdminIT {

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired ObjectMapper mapper;

  // ------------------------------------------------------------------ helpers

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
  private final JwtRequestPostProcessor admin = who(sub(), "ADMIN");

  private JsonNode json(ResultActions r) throws Exception {
    return mapper.readTree(r.andReturn().getResponse().getContentAsString());
  }

  private ResultActions call(
      MockHttpServletRequestBuilder b, JwtRequestPostProcessor who, String body) throws Exception {
    MockHttpServletRequestBuilder req = who == null ? b : b.with(who);
    return mvc.perform(
        body == null ? req : req.contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private String newCourse(JwtRequestPostProcessor by, String tier) throws Exception {
    String slug = "c-" + Ids.newId().toString().substring(24);
    return json(call(
                post("/api/v1/admin/courses"),
                by,
                "{\"slug\":\"%s\",\"title\":\"Course %s\",\"description\":\"A course\",\"difficulty\":\"beginner\",\"accessTier\":\"%s\"}"
                    .formatted(slug, slug, tier))
            .andExpect(status().isCreated()))
        .get("id")
        .asString();
  }

  private String newSection(String course, String title) throws Exception {
    JsonNode c =
        json(
            call(
                    post("/api/v1/admin/courses/" + course + "/sections"),
                    manager,
                    "{\"title\":\"" + title + "\"}")
                .andExpect(status().isCreated()));
    JsonNode sections = c.get("sections");
    return sections.get(sections.size() - 1).get("id").asString();
  }

  private String newLesson(String course, String section, String slug) throws Exception {
    return json(call(
                post("/api/v1/admin/courses/" + course + "/lessons"),
                manager,
                "{\"sectionId\":\"%s\",\"slug\":\"%s\",\"title\":\"Lesson %s\"}"
                    .formatted(section, slug, slug))
            .andExpect(status().isCreated()))
        .get("id")
        .asString();
  }

  private static final String BLOCKS =
      "[{\"type\":\"markdown\",\"data\":{\"text\":\"# Hello\\n\\nBody\"}},{\"type\":\"code\",\"data\":{\"language\":\"java\",\"code\":\"int x = 1;\"}}]";

  private int lessonVersion(String course, String lesson) throws Exception {
    return json(call(get("/api/v1/admin/courses/" + course + "/lessons/" + lesson), manager, null))
        .get("version")
        .asInt();
  }

  private void putBlocks(String course, String lesson, String blocks) throws Exception {
    call(
            put("/api/v1/admin/courses/" + course + "/lessons/" + lesson + "/blocks"),
            manager,
            "{\"blocks\":%s,\"expectedVersion\":%d}"
                .formatted(blocks, lessonVersion(course, lesson)))
        .andExpect(status().isOk());
  }

  private void putSource(String course, String lesson) throws Exception {
    call(
            put("/api/v1/admin/courses/" + course + "/lessons/" + lesson + "/source"),
            manager,
            "{\"kind\":\"original\"}")
        .andExpect(status().isOk());
  }

  private ResultActions publishLesson(String course, String lesson, boolean confirmed)
      throws Exception {
    return call(
        post("/api/v1/admin/courses/" + course + "/lessons/" + lesson + "/publish"),
        manager,
        "{\"originalityConfirmed\":" + confirmed + "}");
  }

  private long audits(String action, String resource) {
    return jdbc.sql("select count(*) from admin.audit_logs where action = :a and resource_id = :r")
        .param("a", action)
        .param("r", resource)
        .query(Long.class)
        .single();
  }

  /** A ready-to-use course with one section and one lesson that has blocks and a source. */
  private String[] readyLesson() throws Exception {
    String course = newCourse(manager, "free");
    String section = newSection(course, "Basics");
    String lesson = newLesson(course, section, "intro");
    putBlocks(course, lesson, BLOCKS);
    putSource(course, lesson);
    return new String[] {course, section, lesson};
  }

  // ------------------------------------------------------------------ authorization

  @Test
  void everyEndpointEnforcesItsPermissionForEveryRole() throws Exception {
    String[] ids = readyLesson();
    String c = ids[0], s = ids[1], l = ids[2];
    UUID random = UUID.randomUUID();
    String base = "/api/v1/admin/courses/" + c;

    record Case(
        String name,
        MockHttpServletRequestBuilder req,
        String body,
        boolean needsPublish,
        boolean needsGlobalEdit) {}
    List<Case> cases =
        List.of(
            new Case("topics", get("/api/v1/admin/topics"), null, false, true),
            new Case("list courses", get("/api/v1/admin/courses"), null, false, true),
            new Case(
                "create course",
                post("/api/v1/admin/courses"),
                "{\"slug\":\"x-%s\",\"title\":\"t\",\"difficulty\":\"beginner\",\"accessTier\":\"free\"}"
                    .formatted(Ids.newId().toString().substring(24)),
                false,
                true),
            new Case("get course", get(base), null, false, false),
            new Case(
                "patch course",
                patch(base),
                "{\"title\":\"new\",\"expectedVersion\":-1}",
                false,
                false),
            new Case("publish course", post(base + "/publish"), null, true, false),
            new Case("unpublish course", post(base + "/unpublish"), null, true, false),
            new Case("archive course", post(base + "/archive"), null, true, false),
            new Case("add section", post(base + "/sections"), "{\"title\":\"S\"}", false, false),
            new Case(
                "reorder sections", put(base + "/sections/order"), "{\"ids\":[]}", false, false),
            new Case(
                "rename section",
                patch(base + "/sections/" + s),
                "{\"title\":\"R\"}",
                false,
                false),
            new Case("delete section", delete(base + "/sections/" + random), null, false, false),
            new Case(
                "reorder lessons",
                put(base + "/sections/" + s + "/lessons/order"),
                "{\"ids\":[\"" + l + "\"]}",
                false,
                false),
            new Case(
                "add lesson",
                post(base + "/lessons"),
                "{\"sectionId\":\""
                    + s
                    + "\",\"slug\":\"z-"
                    + Ids.newId().toString().substring(24)
                    + "\",\"title\":\"Z\"}",
                false,
                false),
            new Case("get lesson", get(base + "/lessons/" + l), null, false, false),
            new Case(
                "patch lesson",
                patch(base + "/lessons/" + l),
                "{\"title\":\"T\",\"expectedVersion\":-1}",
                false,
                false),
            new Case(
                "blocks",
                put(base + "/lessons/" + l + "/blocks"),
                "{\"blocks\":[],\"expectedVersion\":-1}",
                false,
                false),
            new Case(
                "source",
                put(base + "/lessons/" + l + "/source"),
                "{\"kind\":\"original\"}",
                false,
                false),
            new Case(
                "publish lesson",
                post(base + "/lessons/" + l + "/publish"),
                "{\"originalityConfirmed\":false}",
                true,
                false),
            new Case(
                "unpublish lesson", post(base + "/lessons/" + l + "/unpublish"), null, true, false),
            new Case("delete lesson", delete(base + "/lessons/" + random), null, false, false));

    String instructorSub = sub();
    UUID instructorId = provision(instructorSub, "INSTRUCTOR");
    grant(instructorId, c);
    JwtRequestPostProcessor instructor = who(instructorSub, "INSTRUCTOR");
    JwtRequestPostProcessor unassigned = who(sub(), "INSTRUCTOR");
    List<JwtRequestPostProcessor> denied =
        List.of(who(sub()), who(sub(), "MODERATOR"), who(sub(), "PROBLEM_SETTER"), unassigned);

    for (Case k : cases) {
      call(k.req(), null, k.body()).andExpect(status().isUnauthorized());
      for (var d : denied) {
        call(k.req(), d, k.body()).andExpect(status().isForbidden());
      }
      // the assigned instructor: scoped edit on THIS course only
      boolean instructorAllowed = !k.needsPublish() && !k.needsGlobalEdit();
      int got = call(k.req(), instructor, k.body()).andReturn().getResponse().getStatus();
      if (instructorAllowed) {
        assertThat(got).as("assigned instructor: " + k.name()).isNotIn(401, 403);
      } else {
        assertThat(got).as("assigned instructor: " + k.name()).isEqualTo(403);
      }
      // managers and admins hold the global permissions
      for (var staff : List.of(manager, admin)) {
        assertThat(call(k.req(), staff, k.body()).andReturn().getResponse().getStatus())
            .as("staff: " + k.name())
            .isNotIn(401, 403);
      }
    }
  }

  @Test
  void aScopedInstructorCannotTouchAnotherCourse() throws Exception {
    String mine = newCourse(manager, "free");
    String other = newCourse(manager, "free");
    String otherSection = newSection(other, "Other");
    String otherLesson = newLesson(other, otherSection, "secret");

    String sub = sub();
    UUID id = provision(sub, "INSTRUCTOR");
    grant(id, mine);
    var instructor = who(sub, "INSTRUCTOR");

    call(get("/api/v1/admin/courses/" + other), instructor, null).andExpect(status().isForbidden());
    call(get("/api/v1/admin/courses/" + other + "/lessons/" + otherLesson), instructor, null)
        .andExpect(status().isForbidden());
    // using the permitted course id with a lesson of another course must not work either
    call(get("/api/v1/admin/courses/" + mine + "/lessons/" + otherLesson), instructor, null)
        .andExpect(status().isNotFound());
    call(
            put("/api/v1/admin/courses/" + mine + "/lessons/" + otherLesson + "/blocks"),
            instructor,
            "{\"blocks\":[],\"expectedVersion\":0}")
        .andExpect(status().isNotFound());
    // the instructor may author in their own course
    String section = newSectionAs(mine, instructor);
    assertThat(section).isNotBlank();
  }

  private String newSectionAs(String course, JwtRequestPostProcessor by) throws Exception {
    JsonNode c =
        json(
            call(post("/api/v1/admin/courses/" + course + "/sections"), by, "{\"title\":\"Mine\"}")
                .andExpect(status().isCreated()));
    return c.get("sections").get(0).get("id").asString();
  }

  private UUID provision(String sub, String... roles) throws Exception {
    JsonNode me =
        json(mvc.perform(get("/api/v1/me").with(who(sub, roles))).andExpect(status().isOk()));
    return UUID.fromString(me.get("id").asString());
  }

  private void grant(UUID userId, String courseId) throws Exception {
    call(
            post("/api/v1/admin/users/" + userId + "/grants"),
            who(sub(), "SUPER_ADMIN"),
            "{\"permission\":\"course.edit\",\"resourceType\":\"course\",\"resourceId\":\""
                + courseId
                + "\"}")
        .andExpect(status().isCreated());
  }

  // ------------------------------------------------------------------ workflow

  @Test
  void draftToPublishedWorkflowAndSnapshotIsolation() throws Exception {
    String[] ids = readyLesson();
    String c = ids[0], l = ids[2];

    publishLesson(c, l, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("published"))
        .andExpect(jsonPath("$.hasUnpublishedChanges").value(false));
    String snapshot =
        jdbc.sql("select published_snapshot::text from catalog.lessons where id = :l")
            .param("l", UUID.fromString(l))
            .query(String.class)
            .single();
    assertThat(snapshot).contains("# Hello").contains("int x = 1;");

    call(post("/api/v1/admin/courses/" + c + "/publish"), manager, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("published"));

    // editing after publishing changes the draft only
    putBlocks(c, l, "[{\"type\":\"markdown\",\"data\":{\"text\":\"edited draft\"}}]");
    call(get("/api/v1/admin/courses/" + c + "/lessons/" + l), manager, null)
        .andExpect(jsonPath("$.hasUnpublishedChanges").value(true))
        .andExpect(jsonPath("$.status").value("published"));
    assertThat(
            jdbc.sql("select published_snapshot::text from catalog.lessons where id = :l")
                .param("l", UUID.fromString(l))
                .query(String.class)
                .single())
        .as("learners still get the published snapshot")
        .isEqualTo(snapshot);

    publishLesson(c, l, true).andExpect(status().isOk());
    assertThat(
            jdbc.sql("select published_snapshot::text from catalog.lessons where id = :l")
                .param("l", UUID.fromString(l))
                .query(String.class)
                .single())
        .contains("edited draft")
        .doesNotContain("int x = 1;");

    call(post("/api/v1/admin/courses/" + c + "/lessons/" + l + "/unpublish"), manager, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("draft"));
    assertThat(
            jdbc.sql("select published_snapshot from catalog.lessons where id = :l")
                .param("l", UUID.fromString(l))
                .query(String.class)
                .optional())
        .isEmpty();

    assertThat(audits("lesson.published", l)).isEqualTo(2);
    assertThat(audits("lesson.unpublished", l)).isEqualTo(1);
    assertThat(audits("course.published", c)).isEqualTo(1);
    assertThat(
            jdbc.sql(
                    "select count(*) from shared.outbox where event_type = 'catalog.lesson.published' and aggregate_id = :a")
                .param("a", l)
                .query(Long.class)
                .single())
        .isEqualTo(2);
  }

  @Test
  void publishGatesAreEnforced() throws Exception {
    String course = newCourse(manager, "free");
    String section = newSection(course, "S");
    String lesson = newLesson(course, section, "gates");

    // each gate is isolated: everything else is in place when one is missing
    putBlocks(course, lesson, BLOCKS);
    publishLesson(course, lesson, true)
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail")
                .value(org.hamcrest.Matchers.containsString("source"))); // no source
    putSource(course, lesson);
    putBlocks(course, lesson, "[]");
    publishLesson(course, lesson, true)
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("block"))); // no blocks
    putBlocks(course, lesson, BLOCKS);
    publishLesson(course, lesson, false)
        .andExpect(status().isBadRequest()); // originality not confirmed
    call(post("/api/v1/admin/courses/" + course + "/publish"), manager, null)
        .andExpect(status().isConflict()); // no published lesson
    publishLesson(course, lesson, true).andExpect(status().isOk());

    // sign-off is recorded
    assertThat(
            jdbc.sql("select reviewed_by from catalog.content_sources where entity_id = :l")
                .param("l", UUID.fromString(lesson))
                .query(UUID.class)
                .optional())
        .isPresent();
    // changing the source clears the sign-off
    call(
            put("/api/v1/admin/courses/" + course + "/lessons/" + lesson + "/source"),
            manager,
            "{\"kind\":\"ai_assisted\"}")
        .andExpect(status().isOk());
    assertThat(
            jdbc.sql("select reviewed_at from catalog.content_sources where entity_id = :l")
                .param("l", UUID.fromString(lesson))
                .query(java.sql.Timestamp.class)
                .optional())
        .isEmpty();
  }

  @Test
  void licensedSourcesNeedLicenseAndAttribution() throws Exception {
    String[] ids = readyLesson();
    String url = "/api/v1/admin/courses/" + ids[0] + "/lessons/" + ids[2] + "/source";
    call(put(url), manager, "{\"kind\":\"open_license\"}").andExpect(status().isBadRequest());
    call(
            put(url),
            manager,
            "{\"kind\":\"open_license\",\"license\":\"CC0-1.0\",\"attribution\":\"Someone\"}")
        .andExpect(status().isOk());
    call(put(url), manager, "{\"kind\":\"original\",\"sourceUrl\":\"javascript:alert(1)\"}")
        .andExpect(status().isBadRequest());
    call(put(url), manager, "{\"kind\":\"made-up\"}").andExpect(status().isBadRequest());
  }

  // ------------------------------------------------------------------ validation & concurrency

  @Test
  void invalidInputIsRejectedAndMarkupStaysText() throws Exception {
    String[] ids = readyLesson();
    String base = "/api/v1/admin/courses/" + ids[0];
    String blocksUrl = base + "/lessons/" + ids[2] + "/blocks";
    int v = lessonVersion(ids[0], ids[2]);
    String hostile = "<script>alert(1)</script>";
    call(
            put(blocksUrl),
            manager,
            "{\"expectedVersion\":%d,\"blocks\":[{\"type\":\"html\",\"data\":{\"text\":\"x\"}}]}"
                .formatted(v))
        .andExpect(status().isBadRequest());
    call(
            put(blocksUrl),
            manager,
            "{\"expectedVersion\":%d,\"blocks\":[{\"type\":\"markdown\",\"data\":{\"text\":\"x\",\"onclick\":\"y\"}}]}"
                .formatted(v))
        .andExpect(status().isBadRequest());
    call(
            put(blocksUrl),
            manager,
            "{\"expectedVersion\":%d,\"blocks\":[{\"type\":\"markdown\",\"data\":{\"text\":\"%s\"}}]}"
                .formatted(v, hostile))
        .andExpect(status().isOk());
    call(get(base + "/lessons/" + ids[2]), manager, null)
        .andExpect(jsonPath("$.blocks[0].data.text").value(hostile));

    call(
            post("/api/v1/admin/courses"),
            manager,
            "{\"slug\":\"Bad Slug!\",\"title\":\"t\",\"difficulty\":\"beginner\",\"accessTier\":\"free\"}")
        .andExpect(status().isBadRequest());
    call(
            post("/api/v1/admin/courses"),
            manager,
            "{\"slug\":\"ok-slug\",\"title\":\"t\",\"difficulty\":\"expert\",\"accessTier\":\"free\"}")
        .andExpect(status().isBadRequest());
    call(
            post("/api/v1/admin/courses"),
            manager,
            "{\"slug\":\"ok-slug2\",\"title\":\"t\",\"difficulty\":\"beginner\",\"accessTier\":\"free\",\"topicId\":\""
                + UUID.randomUUID()
                + "\"}")
        .andExpect(status().isBadRequest());
    call(
            post(base + "/lessons"),
            manager,
            "{\"sectionId\":\"" + UUID.randomUUID() + "\",\"slug\":\"x\",\"title\":\"t\"}")
        .andExpect(status().isNotFound());
  }

  @Test
  void staleVersionsAreRejectedNotOverwritten() throws Exception {
    String[] ids = readyLesson();
    String c = ids[0], l = ids[2];
    int v = lessonVersion(c, l);
    String url = "/api/v1/admin/courses/" + c + "/lessons/" + l + "/blocks";
    call(
            put(url),
            manager,
            "{\"expectedVersion\":%d,\"blocks\":[{\"type\":\"markdown\",\"data\":{\"text\":\"first\"}}]}"
                .formatted(v))
        .andExpect(status().isOk());
    call(
            put(url),
            manager,
            "{\"expectedVersion\":%d,\"blocks\":[{\"type\":\"markdown\",\"data\":{\"text\":\"second\"}}]}"
                .formatted(v))
        .andExpect(status().isConflict());
    call(get("/api/v1/admin/courses/" + c + "/lessons/" + l), manager, null)
        .andExpect(jsonPath("$.blocks[0].data.text").value("first"));

    int cv = json(call(get("/api/v1/admin/courses/" + c), manager, null)).get("version").asInt();
    call(
            patch("/api/v1/admin/courses/" + c),
            manager,
            "{\"title\":\"A\",\"expectedVersion\":%d}".formatted(cv))
        .andExpect(status().isOk());
    call(
            patch("/api/v1/admin/courses/" + c),
            manager,
            "{\"title\":\"B\",\"expectedVersion\":%d}".formatted(cv))
        .andExpect(status().isConflict());
    call(get("/api/v1/admin/courses/" + c), manager, null)
        .andExpect(jsonPath("$.title").value("A"));
  }

  @Test
  void duplicateSlugsAreConflicts() throws Exception {
    String course = newCourse(manager, "free");
    String section = newSection(course, "S");
    newLesson(course, section, "same");
    call(
            post("/api/v1/admin/courses/" + course + "/lessons"),
            manager,
            "{\"sectionId\":\"" + section + "\",\"slug\":\"same\",\"title\":\"Again\"}")
        .andExpect(status().isConflict());
    String slug =
        json(call(get("/api/v1/admin/courses/" + course), manager, null)).get("slug").asString();
    call(
            post("/api/v1/admin/courses"),
            manager,
            "{\"slug\":\"%s\",\"title\":\"t\",\"difficulty\":\"beginner\",\"accessTier\":\"free\"}"
                .formatted(slug))
        .andExpect(status().isConflict());
  }

  // ------------------------------------------------------------------ structure

  @Test
  void sectionsAndLessonsCanBeReorderedAndDeletedUnderRules() throws Exception {
    String course = newCourse(manager, "free");
    String s1 = newSection(course, "One");
    String s2 = newSection(course, "Two");
    String a = newLesson(course, s1, "a");
    String b = newLesson(course, s1, "b");
    String base = "/api/v1/admin/courses/" + course;

    call(put(base + "/sections/order"), manager, "{\"ids\":[\"%s\",\"%s\"]}".formatted(s2, s1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sections[0].id").value(s2));
    call(put(base + "/sections/order"), manager, "{\"ids\":[\"%s\"]}".formatted(s1))
        .andExpect(status().isBadRequest()); // incomplete
    call(put(base + "/sections/order"), manager, "{\"ids\":[\"%s\",\"%s\"]}".formatted(s1, s1))
        .andExpect(status().isBadRequest()); // duplicate

    call(
            put(base + "/sections/" + s1 + "/lessons/order"),
            manager,
            "{\"ids\":[\"%s\",\"%s\"]}".formatted(b, a))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sections[1].lessons[0].id").value(b));

    call(delete(base + "/sections/" + s1), manager, null)
        .andExpect(status().isConflict()); // has lessons
    call(
            patch(base + "/lessons/" + a),
            manager,
            "{\"sectionId\":\"%s\",\"expectedVersion\":%d}".formatted(s2, lessonVersion(course, a)))
        .andExpect(status().isOk());
    call(delete(base + "/lessons/" + b), manager, null).andExpect(status().isNoContent());
    call(delete(base + "/sections/" + s1), manager, null).andExpect(status().isOk());

    // a published lesson cannot be deleted
    putBlocks(course, a, BLOCKS);
    putSource(course, a);
    publishLesson(course, a, true).andExpect(status().isOk());
    call(delete(base + "/lessons/" + a), manager, null).andExpect(status().isConflict());
  }

  @Test
  void topicsAreSeeded() throws Exception {
    call(get("/api/v1/admin/topics"), manager, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(31)))
        .andExpect(jsonPath("$[0].slug").value("arrays"));
  }
}
