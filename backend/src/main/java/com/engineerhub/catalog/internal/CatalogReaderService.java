package com.engineerhub.catalog.internal;

import com.engineerhub.billing.EntitlementService;
import com.engineerhub.catalog.internal.CatalogDtos.TopicView;
import com.engineerhub.catalog.internal.CatalogReaderDtos.PublicBlock;
import com.engineerhub.catalog.internal.CatalogReaderDtos.PublicCourse;
import com.engineerhub.catalog.internal.CatalogReaderDtos.PublicCourseDetail;
import com.engineerhub.catalog.internal.CatalogReaderDtos.PublicCoursePage;
import com.engineerhub.catalog.internal.CatalogReaderDtos.PublicLesson;
import com.engineerhub.catalog.internal.CatalogReaderDtos.PublicLessonRef;
import com.engineerhub.catalog.internal.CatalogReaderDtos.PublicOutlineLesson;
import com.engineerhub.catalog.internal.CatalogReaderDtos.PublicOutlineSection;
import com.engineerhub.catalog.internal.CatalogReaderDtos.PublicTopic;
import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reader side of the catalog. Serves only <em>published</em> courses and the published
 * <em>snapshot</em> of published lessons; premium bodies are released only when the lesson is free
 * (or a free preview) or {@link EntitlementService} says yes. The gating decision is made here, on
 * the server, for every request.
 */
@Service
class CatalogReaderService {

  private static final Set<String> DIFFICULTIES = Set.of("beginner", "intermediate", "advanced");
  private static final Set<String> TIERS = Set.of("free", "premium");
  private static final java.util.regex.Pattern SLUG =
      java.util.regex.Pattern.compile(CatalogDtos.SLUG);

  private final JdbcClient jdbc;
  private final ObjectMapper mapper;
  private final EntitlementService entitlements;

  CatalogReaderService(JdbcClient jdbc, ObjectMapper mapper, EntitlementService entitlements) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.entitlements = entitlements;
  }

  @Transactional(readOnly = true)
  List<TopicView> topics() {
    return jdbc.sql("select id, slug, name, position from catalog.topics order by position")
        .query(
            (rs, i) ->
                new TopicView(
                    rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getInt(4)))
        .list();
  }

  // ------------------------------------------------------------------ course list

  @Transactional(readOnly = true)
  PublicCoursePage courses(
      String topic, String difficulty, String tier, String q, int page, int size) {
    if (topic != null && !SLUG.matcher(topic).matches()) {
      throw invalid("topic is not a valid slug");
    }
    if (difficulty != null && !DIFFICULTIES.contains(difficulty)) {
      throw invalid("difficulty must be one of beginner, intermediate, advanced");
    }
    if (tier != null && !TIERS.contains(tier)) {
      throw invalid("tier must be free or premium");
    }
    if (q != null && q.length() > 100) {
      throw invalid("q is too long");
    }
    int pageSize = Math.max(1, Math.min(size, 50));
    int offset = Math.max(0, Math.min(page, 100_000)) * pageSize;

    StringBuilder where = new StringBuilder(" where c.status = 'published'");
    Map<String, Object> p = new LinkedHashMap<>();
    if (topic != null) {
      where.append(" and t.slug = :topic");
      p.put("topic", topic);
    }
    if (difficulty != null) {
      where.append(" and c.difficulty = :diff");
      p.put("diff", difficulty);
    }
    if (tier != null) {
      where.append(" and c.access_tier = :tier");
      p.put("tier", tier);
    }
    if (q != null && !q.isBlank()) {
      // user input is data: LIKE wildcards are escaped so "%" or "_" search literally
      where.append(" and (c.title ilike :q escape '\\' or c.description ilike :q escape '\\')");
      p.put(
          "q", "%" + q.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
    }
    String from = " from catalog.courses c left join catalog.topics t on t.id = c.topic_id" + where;

    var count = jdbc.sql("select count(*)" + from);
    p.forEach(count::param);
    long total = count.query(Long.class).single();

    var rows =
        jdbc.sql(
            "select c.slug, c.title, c.description, t.slug, t.name, c.difficulty, c.access_tier, c.est_minutes,"
                + " (select count(*) from catalog.lessons l where l.course_id = c.id and l.status = 'published'), c.published_at"
                + from
                + " order by c.published_at desc, c.id limit :limit offset :offset");
    p.forEach(rows::param);
    List<PublicCourse> items =
        rows.param("limit", pageSize)
            .param("offset", offset)
            .query((rs, i) -> mapCourse(rs))
            .list();
    return new PublicCoursePage(items, offset / pageSize, pageSize, total);
  }

  // ------------------------------------------------------------------ course detail

  @Transactional(readOnly = true)
  PublicCourseDetail course(String slug) {
    requireSlug(slug, "Course not found.");
    var row =
        jdbc.sql(
                "select c.slug, c.title, c.description, t.slug, t.name, c.difficulty, c.access_tier, c.est_minutes,"
                    + " (select count(*) from catalog.lessons l where l.course_id = c.id and l.status = 'published'), c.published_at, c.id"
                    + " from catalog.courses c left join catalog.topics t on t.id = c.topic_id"
                    + " where c.slug = :s and c.status = 'published'")
            .param("s", slug)
            .query((rs, i) -> new Object[] {mapCourse(rs), rs.getObject(11, UUID.class)})
            .optional()
            .orElseThrow(() -> ApiException.notFound("Course not found."));
    UUID id = (UUID) row[1];

    Map<UUID, PublicOutlineSection> sections = new LinkedHashMap<>();
    Map<UUID, List<PublicOutlineLesson>> lessons = new LinkedHashMap<>();
    jdbc.sql(
            "select s.id, s.title, s.position, l.slug, l.title, l.position, l.access_tier, l.free_preview, l.est_read_min, l.id"
                + " from catalog.sections s join catalog.lessons l on l.section_id = s.id and l.status = 'published'"
                + " where s.course_id = :c order by s.position, l.position")
        .param("c", id)
        .query(
            (rs, i) -> {
              UUID sid = rs.getObject(1, UUID.class);
              sections.computeIfAbsent(sid, k -> null);
              lessons.computeIfAbsent(sid, k -> new ArrayList<>());
              lessons
                  .get(sid)
                  .add(
                      new PublicOutlineLesson(
                          rs.getObject(10, UUID.class),
                          rs.getString(4),
                          rs.getString(5),
                          rs.getInt(6),
                          rs.getString(7),
                          rs.getBoolean(8),
                          (Integer) rs.getObject(9)));
              sections.put(sid, new PublicOutlineSection(rs.getString(2), rs.getInt(3), null));
              return null;
            })
        .list();
    List<PublicOutlineSection> outline = new ArrayList<>();
    sections.forEach(
        (sid, s) ->
            outline.add(new PublicOutlineSection(s.title(), s.position(), lessons.get(sid))));
    return new PublicCourseDetail((PublicCourse) row[0], id, outline);
  }

  // ------------------------------------------------------------------ lesson

  /** Result of a lesson request: the lesson plus whether the body is the same for every caller. */
  record LessonResult(PublicLesson lesson, boolean sameForEveryone) {}

  @Transactional(readOnly = true)
  LessonResult lesson(Authentication caller, String courseSlug, String lessonSlug) {
    requireSlug(courseSlug, "Lesson not found.");
    requireSlug(lessonSlug, "Lesson not found.");
    var row =
        jdbc.sql(
                "select c.id, l.id, l.title, l.access_tier, l.free_preview, l.published_snapshot::text, l.published_at, l.section_id, l.position"
                    + " from catalog.lessons l join catalog.courses c on c.id = l.course_id"
                    + " where c.slug = :cs and l.slug = :ls and c.status = 'published' and l.status = 'published'")
            .param("cs", courseSlug)
            .param("ls", lessonSlug)
            .query(
                (rs, i) ->
                    new Object[] {
                      rs.getObject(1, UUID.class),
                      rs.getObject(2, UUID.class),
                      rs.getString(3),
                      rs.getString(4),
                      rs.getBoolean(5),
                      rs.getString(6),
                      rs.getTimestamp(7).toInstant()
                    })
            .optional()
            .orElseThrow(() -> ApiException.notFound("Lesson not found."));
    UUID courseId = (UUID) row[0];
    UUID lessonId = (UUID) row[1];
    String tier = (String) row[3];
    boolean freePreview = (Boolean) row[4];

    boolean open = tier.equals("free") || freePreview;
    if (!open && !entitlements.canReadPremium(caller, "course", courseId)) {
      throw new ApiException(
          ErrorCode.ENTITLEMENT_REQUIRED, "This lesson is part of the premium content.");
    }

    JsonNode snapshot = mapper.readTree((String) row[5]);
    List<PublicBlock> blocks = new ArrayList<>();
    for (JsonNode b : snapshot.get("blocks")) {
      @SuppressWarnings("unchecked")
      Map<String, Object> data = mapper.convertValue(b.get("data"), Map.class);
      blocks.add(new PublicBlock(b.get("type").asString(), data));
    }
    Integer est = snapshot.hasNonNull("estReadMin") ? snapshot.get("estReadMin").asInt() : null;

    // neighbours in reading order across sections, published lessons only
    List<Object[]> order =
        jdbc.sql(
                "select l.id, l.slug, l.title from catalog.lessons l join catalog.sections s on s.id = l.section_id"
                    + " where l.course_id = :c and l.status = 'published' order by s.position, l.position")
            .param("c", courseId)
            .query(
                (rs, i) ->
                    new Object[] {rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)})
            .list();
    PublicLessonRef prev = null;
    PublicLessonRef next = null;
    for (int i = 0; i < order.size(); i++) {
      if (order.get(i)[0].equals(lessonId)) {
        if (i > 0) {
          prev = new PublicLessonRef((String) order.get(i - 1)[1], (String) order.get(i - 1)[2]);
        }
        if (i + 1 < order.size()) {
          next = new PublicLessonRef((String) order.get(i + 1)[1], (String) order.get(i + 1)[2]);
        }
      }
    }
    PublicLesson out =
        new PublicLesson(
            lessonId,
            courseSlug,
            lessonSlug,
            snapshot.get("title").asString(),
            tier,
            freePreview,
            est,
            blocks,
            prev,
            next,
            (java.time.Instant) row[6]);
    return new LessonResult(out, open);
  }

  // ------------------------------------------------------------------ helpers

  private static PublicCourse mapCourse(java.sql.ResultSet rs) throws java.sql.SQLException {
    PublicTopic topic =
        rs.getString(4) == null ? null : new PublicTopic(rs.getString(4), rs.getString(5));
    return new PublicCourse(
        rs.getString(1),
        rs.getString(2),
        rs.getString(3),
        topic,
        rs.getString(6),
        rs.getString(7),
        (Integer) rs.getObject(8),
        rs.getInt(9),
        rs.getTimestamp(10).toInstant());
  }

  private static void requireSlug(String slug, String message) {
    if (slug == null || slug.length() > 80 || !SLUG.matcher(slug).matches()) {
      throw ApiException.notFound(message); // malformed path values are simply "not found"
    }
  }

  private static ApiException invalid(String detail) {
    return new ApiException(ErrorCode.VALIDATION_FAILED, detail);
  }
}
