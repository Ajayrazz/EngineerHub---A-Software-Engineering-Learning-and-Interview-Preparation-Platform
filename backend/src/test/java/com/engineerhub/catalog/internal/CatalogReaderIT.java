package com.engineerhub.catalog.internal;

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

/** The public reader API: visibility, snapshot-only content and server-side premium gating. */
@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogReaderIT {

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
  private final JwtRequestPostProcessor learner = who(sub());

  private ResultActions call(
      MockHttpServletRequestBuilder b, JwtRequestPostProcessor who, String body) throws Exception {
    MockHttpServletRequestBuilder req = who == null ? b : b.with(who);
    return mvc.perform(
        body == null ? req : req.contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private JsonNode json(ResultActions r) throws Exception {
    return mapper.readTree(r.andReturn().getResponse().getContentAsString());
  }

  // fixture ids
  private String tag; // unique word so searches only hit this class's data
  private String premiumCourse; // slug
  private String premiumCourseId;
  private String otherPremiumCourseId;
  private String freeCourse;
  private String draftCourse;
  private String archivedCourse;
  private String lessonFree = "free-intro",
      lessonPremium = "premium-deep",
      lessonPreview = "premium-preview",
      lessonDraft = "never-published";
  private String editedLessonId;

  private String createCourse(String slug, String title, String tier, String topicSlug)
      throws Exception {
    String topicId =
        topicSlug == null
            ? null
            : jdbc.sql("select id from catalog.topics where slug = :s")
                .param("s", topicSlug)
                .query(UUID.class)
                .single()
                .toString();
    return json(call(
            post("/api/v1/admin/courses"),
            manager,
            "{\"slug\":\"%s\",\"title\":\"%s\",\"description\":\"About %s\",\"difficulty\":\"intermediate\",\"accessTier\":\"%s\"%s}"
                .formatted(
                    slug,
                    title,
                    title,
                    tier,
                    topicId == null ? "" : ",\"topicId\":\"" + topicId + "\"")))
        .get("id")
        .asString();
  }

  private String section(String course, String title) throws Exception {
    JsonNode c =
        json(
            call(
                post("/api/v1/admin/courses/" + course + "/sections"),
                manager,
                "{\"title\":\"" + title + "\"}"));
    return c.get("sections").get(c.get("sections").size() - 1).get("id").asString();
  }

  private String lesson(String course, String section, String slug, String text) throws Exception {
    String id =
        json(call(
                post("/api/v1/admin/courses/" + course + "/lessons"),
                manager,
                "{\"sectionId\":\"%s\",\"slug\":\"%s\",\"title\":\"Lesson %s\"}"
                    .formatted(section, slug, slug)))
            .get("id")
            .asString();
    setBlocks(course, id, text);
    call(
            put("/api/v1/admin/courses/" + course + "/lessons/" + id + "/source"),
            manager,
            "{\"kind\":\"original\"}")
        .andExpect(status().isOk());
    return id;
  }

  private void setBlocks(String course, String lesson, String text) throws Exception {
    int v =
        json(call(get("/api/v1/admin/courses/" + course + "/lessons/" + lesson), manager, null))
            .get("version")
            .asInt();
    call(
            put("/api/v1/admin/courses/" + course + "/lessons/" + lesson + "/blocks"),
            manager,
            "{\"expectedVersion\":%d,\"blocks\":[{\"type\":\"markdown\",\"data\":{\"text\":\"%s\"}}]}"
                .formatted(v, text))
        .andExpect(status().isOk());
  }

  private void tune(String course, String lesson, String tier, boolean preview) throws Exception {
    int v =
        json(call(get("/api/v1/admin/courses/" + course + "/lessons/" + lesson), manager, null))
            .get("version")
            .asInt();
    call(
            patch("/api/v1/admin/courses/" + course + "/lessons/" + lesson),
            manager,
            "{\"accessTier\":\"%s\",\"freePreview\":%s,\"expectedVersion\":%d}"
                .formatted(tier, preview, v))
        .andExpect(status().isOk());
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
    tag = "zq" + Ids.newId().toString().substring(26).replaceAll("[^a-z0-9]", "x");
    premiumCourse = "premium-" + tag;
    premiumCourseId = createCourse(premiumCourse, "Premium " + tag + " guide", "premium", "graphs");
    String sa = section(premiumCourseId, "Part one");
    String sb = section(premiumCourseId, "Part two");

    String freeId = lesson(premiumCourseId, sa, lessonFree, "FREE-BODY-" + tag);
    tune(premiumCourseId, freeId, "free", false);
    publish(premiumCourseId, freeId);
    String premiumId = lesson(premiumCourseId, sa, lessonPremium, "PREMIUM-SECRET-" + tag);
    publish(premiumCourseId, premiumId);
    String previewId = lesson(premiumCourseId, sb, lessonPreview, "PREVIEW-BODY-" + tag);
    tune(premiumCourseId, previewId, "premium", true);
    publish(premiumCourseId, previewId);
    lesson(premiumCourseId, sb, lessonDraft, "DRAFT-ONLY-" + tag); // never published
    editedLessonId = freeId;
    call(post("/api/v1/admin/courses/" + premiumCourseId + "/publish"), manager, null)
        .andExpect(status().isOk());

    otherPremiumCourseId = createCourse("other-" + tag, "Other " + tag, "premium", "arrays");
    String so = section(otherPremiumCourseId, "S");
    String otherLesson = lesson(otherPremiumCourseId, so, "other-secret", "OTHER-SECRET-" + tag);
    publish(otherPremiumCourseId, otherLesson);
    call(post("/api/v1/admin/courses/" + otherPremiumCourseId + "/publish"), manager, null)
        .andExpect(status().isOk());

    freeCourse = "free-" + tag;
    String freeCourseId = createCourse(freeCourse, "Free " + tag + " basics", "free", "arrays");
    String sf = section(freeCourseId, "S");
    String fl = lesson(freeCourseId, sf, "hello", "HELLO-" + tag);
    publish(freeCourseId, fl);
    call(post("/api/v1/admin/courses/" + freeCourseId + "/publish"), manager, null)
        .andExpect(status().isOk());

    draftCourse = "draft-" + tag;
    createCourse(draftCourse, "Draft " + tag, "free", null);
    archivedCourse = "archived-" + tag;
    String archId = createCourse(archivedCourse, "Archived " + tag, "free", null);
    String sar = section(archId, "S");
    String al = lesson(archId, sar, "old", "OLD-" + tag);
    publish(archId, al);
    call(post("/api/v1/admin/courses/" + archId + "/publish"), manager, null)
        .andExpect(status().isOk());
    call(post("/api/v1/admin/courses/" + archId + "/archive"), manager, null)
        .andExpect(status().isOk());
  }

  // ------------------------------------------------------------------ listing

  @Test
  void listsOnlyPublishedCoursesAndNeverLeaksDraftsOrArchived() throws Exception {
    JsonNode page =
        json(call(get("/api/v1/courses?q=" + tag), null, null).andExpect(status().isOk()));
    var slugs = new java.util.ArrayList<String>();
    page.get("items").forEach(i -> slugs.add(i.get("slug").asString()));
    assertThat(slugs).containsExactlyInAnyOrder(premiumCourse, "other-" + tag, freeCourse);
    assertThat(page.toString()).doesNotContain(draftCourse).doesNotContain(archivedCourse);
    assertThat(page.get("total").asInt()).isEqualTo(3);
    assertThat(page.get("items").get(0).has("id")).as("list items carry no internal ids").isFalse();
  }

  @Test
  void filtersAndPaging() throws Exception {
    call(get("/api/v1/courses?q=" + tag + "&topic=graphs"), null, null)
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].slug").value(premiumCourse));
    call(get("/api/v1/courses?q=" + tag + "&tier=free"), null, null)
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].slug").value(freeCourse));
    call(get("/api/v1/courses?q=" + tag + "&difficulty=advanced"), null, null)
        .andExpect(jsonPath("$.total").value(0));
    call(get("/api/v1/courses?q=" + tag + "&size=2&page=1"), null, null)
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.total").value(3));
    call(get("/api/v1/courses?q=" + tag + "&size=1000"), null, null)
        .andExpect(jsonPath("$.size").value(50));
    call(get("/api/v1/courses?difficulty=expert"), null, null).andExpect(status().isBadRequest());
    call(get("/api/v1/courses?tier=gold"), null, null).andExpect(status().isBadRequest());
    call(get("/api/v1/courses?topic=Not%20A%20Slug"), null, null)
        .andExpect(status().isBadRequest());
    call(get("/api/v1/courses?q=" + "x".repeat(101)), null, null)
        .andExpect(status().isBadRequest());
    // wildcard characters search literally
    call(get("/api/v1/courses?q=%25"), null, null).andExpect(jsonPath("$.total").value(0));
    call(get("/api/v1/courses?q=_"), null, null).andExpect(jsonPath("$.total").value(0));
  }

  // ------------------------------------------------------------------ outline

  @Test
  void courseOutlineListsPublishedLessonsOnly() throws Exception {
    JsonNode c =
        json(
            call(get("/api/v1/courses/" + premiumCourse), null, null)
                .andExpect(status().isOk())
                .andExpect(
                    header()
                        .string("Cache-Control", org.hamcrest.Matchers.containsString("public"))));
    assertThat(c.get("course").get("lessonCount").asInt()).isEqualTo(3);
    var slugs = new java.util.ArrayList<String>();
    c.get("sections")
        .forEach(s -> s.get("lessons").forEach(l -> slugs.add(l.get("slug").asString())));
    assertThat(slugs).containsExactly(lessonFree, lessonPremium, lessonPreview);
    assertThat(c.toString())
        .doesNotContain("FREE-BODY")
        .doesNotContain("PREMIUM-SECRET")
        .doesNotContain(lessonDraft);
    assertThat(c.get("sections").get(0).get("lessons").get(0).get("id").asString()).isNotBlank();
    assertThat(c.get("sections").get(0).get("lessons").get(0).get("accessTier").asString())
        .isEqualTo("free");
    assertThat(c.get("sections").get(1).get("lessons").get(0).get("freePreview").asBoolean())
        .isTrue();
  }

  @Test
  void unknownDraftArchivedAndMalformedCoursesAreNotFound() throws Exception {
    for (String slug :
        new String[] {draftCourse, archivedCourse, "does-not-exist", "UPPER", "a--b"}) {
      assertThat(
              call(get("/api/v1/courses/" + slug), null, null)
                  .andReturn()
                  .getResponse()
                  .getStatus())
          .as("course " + slug)
          .isEqualTo(404);
      call(get("/api/v1/courses/" + slug + "/lessons/x"), null, null)
          .andExpect(status().isNotFound());
    }
    // percent-encoded input may be refused by the HTTP firewall before it reaches the controller:
    // still never a 500
    assertThat(call(get("/api/v1/courses/a%20b"), null, null).andReturn().getResponse().getStatus())
        .isIn(400, 404);
    call(get("/api/v1/courses/" + archivedCourse + "/lessons/old"), null, null)
        .andExpect(status().isNotFound());
    call(get("/api/v1/courses/" + premiumCourse + "/lessons/" + lessonDraft), null, null)
        .andExpect(status().isNotFound());
  }

  // ------------------------------------------------------------------ gating

  @Test
  void freeLessonsAreOpenToEveryoneAndCarryNavigation() throws Exception {
    for (JwtRequestPostProcessor who : new JwtRequestPostProcessor[] {null, learner, manager}) {
      call(get("/api/v1/courses/" + premiumCourse + "/lessons/" + lessonFree), who, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.blocks[0].data.text").value("FREE-BODY-" + tag))
          .andExpect(jsonPath("$.previous").doesNotExist())
          .andExpect(jsonPath("$.next.slug").value(lessonPremium))
          .andExpect(
              header().string("Cache-Control", org.hamcrest.Matchers.containsString("public")));
    }
  }

  @Test
  void premiumBodiesAreWithheldFromAnonymousAndNonEntitledCallers() throws Exception {
    String url = "/api/v1/courses/" + premiumCourse + "/lessons/" + lessonPremium;
    for (JwtRequestPostProcessor who :
        new JwtRequestPostProcessor[] {
          null, learner, who(sub(), "MODERATOR"), who(sub(), "PROBLEM_SETTER")
        }) {
      String body =
          call(get(url), who, null)
              .andExpect(status().isForbidden())
              .andExpect(jsonPath("$.code").value("ENTITLEMENT_REQUIRED"))
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(body).doesNotContain("PREMIUM-SECRET");
    }
  }

  @Test
  void freePreviewOpensAPremiumLesson() throws Exception {
    call(get("/api/v1/courses/" + premiumCourse + "/lessons/" + lessonPreview), null, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.blocks[0].data.text").value("PREVIEW-BODY-" + tag))
        .andExpect(jsonPath("$.previous.slug").value(lessonPremium));
  }

  @Test
  void entitledStaffGetPremiumBodiesWithoutSharedCaching() throws Exception {
    call(get("/api/v1/courses/" + premiumCourse + "/lessons/" + lessonPremium), manager, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.blocks[0].data.text").value("PREMIUM-SECRET-" + tag))
        .andExpect(
            header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    call(
            get("/api/v1/courses/" + premiumCourse + "/lessons/" + lessonPremium),
            who(sub(), "ADMIN"),
            null)
        .andExpect(status().isOk());
  }

  @Test
  void anInstructorIsEntitledOnlyForTheirOwnCourse() throws Exception {
    String instructorSub = sub();
    UUID id =
        UUID.fromString(
            json(mvc.perform(get("/api/v1/me").with(who(instructorSub, "INSTRUCTOR")))
                    .andExpect(status().isOk()))
                .get("id")
                .asString());
    for (String perm : new String[] {"content.premium.read"}) {
      call(
              post("/api/v1/admin/users/" + id + "/grants"),
              who(sub(), "SUPER_ADMIN"),
              "{\"permission\":\"%s\",\"resourceType\":\"course\",\"resourceId\":\"%s\"}"
                  .formatted(perm, premiumCourseId))
          .andExpect(status().isCreated());
    }
    var instructor = who(instructorSub, "INSTRUCTOR");
    call(get("/api/v1/courses/" + premiumCourse + "/lessons/" + lessonPremium), instructor, null)
        .andExpect(status().isOk());
    call(get("/api/v1/courses/other-" + tag + "/lessons/other-secret"), instructor, null)
        .andExpect(status().isForbidden());
  }

  // ------------------------------------------------------------------ snapshot & visibility
  // changes

  @Test
  void readersOnlySeeThePublishedSnapshotAndUnpublishingHidesEverything() throws Exception {
    String tag2 = "sn" + Ids.newId().toString().substring(26).replaceAll("[^a-z0-9]", "x");
    String course = createCourse("snap-" + tag2, "Snap " + tag2, "free", null);
    String s = section(course, "S");
    String l = lesson(course, s, "live", "VERSION-ONE");
    publish(course, l);
    call(post("/api/v1/admin/courses/" + course + "/publish"), manager, null)
        .andExpect(status().isOk());
    String url = "/api/v1/courses/snap-" + tag2 + "/lessons/live";

    call(get(url), null, null).andExpect(jsonPath("$.blocks[0].data.text").value("VERSION-ONE"));
    setBlocks(course, l, "VERSION-TWO-DRAFT");
    call(get(url), null, null).andExpect(jsonPath("$.blocks[0].data.text").value("VERSION-ONE"));
    publish(course, l);
    call(get(url), null, null)
        .andExpect(jsonPath("$.blocks[0].data.text").value("VERSION-TWO-DRAFT"));

    call(post("/api/v1/admin/courses/" + course + "/lessons/" + l + "/unpublish"), manager, null)
        .andExpect(status().isOk());
    call(get(url), null, null).andExpect(status().isNotFound());
    publish(course, l);
    call(post("/api/v1/admin/courses/" + course + "/unpublish"), manager, null)
        .andExpect(status().isOk());
    call(get(url), null, null).andExpect(status().isNotFound());
    call(get("/api/v1/courses/snap-" + tag2), null, null).andExpect(status().isNotFound());
  }

  @Test
  void topicsArePublicAndCacheable() throws Exception {
    call(get("/api/v1/topics"), null, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(31))
        .andExpect(header().exists("Cache-Control"));
  }

  @Test
  void hostileMarkupIsReturnedAsInertText() throws Exception {
    String tag2 = "xs" + Ids.newId().toString().substring(26).replaceAll("[^a-z0-9]", "x");
    String course = createCourse("xss-" + tag2, "Xss " + tag2, "free", null);
    String s = section(course, "S");
    String l = lesson(course, s, "markup", "<img src=x onerror=alert(1)>");
    publish(course, l);
    call(post("/api/v1/admin/courses/" + course + "/publish"), manager, null)
        .andExpect(status().isOk());
    String raw =
        call(get("/api/v1/courses/xss-" + tag2 + "/lessons/markup"), null, null)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(raw).contains("<img src=x onerror=alert(1)>");
    assertThat(
            call(get("/api/v1/courses/xss-" + tag2 + "/lessons/markup"), null, null)
                .andReturn()
                .getResponse()
                .getContentType())
        .startsWith("application/json");
  }
}
