package com.engineerhub.catalog.internal;

import com.engineerhub.catalog.internal.CatalogDtos.BlockInput;
import com.engineerhub.catalog.internal.CatalogDtos.BlockView;
import com.engineerhub.catalog.internal.CatalogDtos.CourseCreate;
import com.engineerhub.catalog.internal.CatalogDtos.CourseListItem;
import com.engineerhub.catalog.internal.CatalogDtos.CoursePatch;
import com.engineerhub.catalog.internal.CatalogDtos.CourseView;
import com.engineerhub.catalog.internal.CatalogDtos.LessonCreate;
import com.engineerhub.catalog.internal.CatalogDtos.LessonPatch;
import com.engineerhub.catalog.internal.CatalogDtos.LessonSummary;
import com.engineerhub.catalog.internal.CatalogDtos.LessonView;
import com.engineerhub.catalog.internal.CatalogDtos.SectionView;
import com.engineerhub.catalog.internal.CatalogDtos.SourceRequest;
import com.engineerhub.catalog.internal.CatalogDtos.SourceView;
import com.engineerhub.catalog.internal.CatalogDtos.TopicView;
import com.engineerhub.identity.UserDirectory;
import com.engineerhub.shared.audit.AuditEvent;
import com.engineerhub.shared.audit.AuditLog;
import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import com.engineerhub.shared.id.Ids;
import com.engineerhub.shared.messaging.OutboxPublisher;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Authoring use cases. Authorization of <em>who</em> may touch <em>which course</em> is decided at
 * the controller ({@code @authz.can('course.edit','course',#courseId)}); every lesson/section
 * lookup here is additionally scoped by that course id, so an id from another course is a 404,
 * never a cross-course edit.
 */
@Service
class CatalogAdminService {

  private final JdbcClient jdbc;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final AuditLog audit;
  private final OutboxPublisher events;
  private final UserDirectory directory;

  CatalogAdminService(
      JdbcClient jdbc,
      ObjectMapper mapper,
      Clock clock,
      AuditLog audit,
      OutboxPublisher events,
      UserDirectory directory) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.clock = clock;
    this.audit = audit;
    this.events = events;
    this.directory = directory;
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }

  // ================================================================= topics

  @Transactional(readOnly = true)
  List<TopicView> topics() {
    return jdbc.sql("select id, slug, name, position from catalog.topics order by position")
        .query(
            (rs, i) ->
                new TopicView(
                    rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getInt(4)))
        .list();
  }

  // ================================================================= courses

  @Transactional
  CourseView createCourse(Authentication actor, CourseCreate r) {
    UUID caller = directory.current(actor).id();
    requireTopic(r.topicId());
    UUID id = Ids.newId();
    try {
      jdbc.sql(
              """
              insert into catalog.courses (id, slug, title, description, topic_id, difficulty, access_tier,
                                           status, version, created_by, created_at, updated_at)
              values (:id, :slug, :title, :desc, :topic, :diff, :tier, 'draft', 0, :by, :now, :now)
              """)
          .param("id", id)
          .param("slug", r.slug())
          .param("title", r.title().strip())
          .param("desc", r.description() == null ? "" : r.description().strip())
          .param("topic", r.topicId())
          .param("diff", r.difficulty())
          .param("tier", r.accessTier())
          .param("by", caller)
          .param("now", now())
          .update();
    } catch (DuplicateKeyException e) {
      throw ApiException.conflict("A course with this slug already exists.");
    }
    audit.record(
        actor,
        caller,
        AuditEvent.of("course.created", "course", id.toString())
            .withChange(null, Map.of("slug", r.slug(), "accessTier", r.accessTier())));
    return course(id);
  }

  @Transactional(readOnly = true)
  List<CourseListItem> courses() {
    return jdbc.sql(
            "select id, slug, title, status, access_tier, difficulty, updated_at from catalog.courses order by updated_at desc, id limit 500")
        .query(
            (rs, i) ->
                new CourseListItem(
                    rs.getObject(1, UUID.class),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getString(5),
                    rs.getString(6),
                    rs.getTimestamp(7).toInstant()))
        .list();
  }

  @Transactional(readOnly = true)
  CourseView course(UUID id) {
    var row =
        jdbc.sql(
                "select slug, title, description, topic_id, difficulty, access_tier, status, est_minutes, version, published_at, created_at, updated_at from catalog.courses where id = :id")
            .param("id", id)
            .query(
                (rs, i) ->
                    new Object[] {
                      rs.getString(1), rs.getString(2), rs.getString(3),
                          rs.getObject(4, UUID.class),
                      rs.getString(5), rs.getString(6), rs.getString(7), (Integer) rs.getObject(8),
                      rs.getInt(9), ts(rs.getTimestamp(10)), ts(rs.getTimestamp(11)),
                          ts(rs.getTimestamp(12))
                    })
            .optional()
            .orElseThrow(() -> ApiException.notFound("Course not found."));

    List<SectionView> sections = new ArrayList<>();
    Map<UUID, List<LessonSummary>> byId = new LinkedHashMap<>();
    var secRows =
        jdbc.sql(
                "select id, title, position from catalog.sections where course_id = :c order by position")
            .param("c", id)
            .query(
                (rs, i) ->
                    new Object[] {rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3)})
            .list();
    for (Object[] s : secRows) {
      byId.put((UUID) s[0], new ArrayList<>());
    }
    jdbc.sql(
            "select id, section_id, slug, title, position, access_tier, free_preview, status, version, (published_at is not null and updated_at > published_at) from catalog.lessons where course_id = :c order by position")
        .param("c", id)
        .query(
            (rs, i) -> {
              byId.get(rs.getObject(2, UUID.class))
                  .add(
                      new LessonSummary(
                          rs.getObject(1, UUID.class),
                          rs.getString(3),
                          rs.getString(4),
                          rs.getInt(5),
                          rs.getString(6),
                          rs.getBoolean(7),
                          rs.getString(8),
                          rs.getInt(9),
                          rs.getBoolean(10)));
              return null;
            })
        .list();
    for (Object[] s : secRows) {
      sections.add(
          new SectionView((UUID) s[0], (String) s[1], (Integer) s[2], byId.get((UUID) s[0])));
    }
    return new CourseView(
        id,
        (String) row[0],
        (String) row[1],
        (String) row[2],
        (UUID) row[3],
        (String) row[4],
        (String) row[5],
        (String) row[6],
        (Integer) row[7],
        (Integer) row[8],
        (Instant) row[9],
        (Instant) row[10],
        (Instant) row[11],
        sections);
  }

  @Transactional
  CourseView patchCourse(UUID id, CoursePatch p) {
    requireTopic(p.topicId());
    int n =
        jdbc.sql(
                """
                update catalog.courses set
                  title = coalesce(:title, title), description = coalesce(:desc, description),
                  topic_id = coalesce(:topic, topic_id), difficulty = coalesce(:diff, difficulty),
                  access_tier = coalesce(:tier, access_tier), est_minutes = coalesce(:est, est_minutes),
                  version = version + 1, updated_at = :now
                where id = :id and version = :v
                """)
            .param("title", p.title() == null ? null : p.title().strip())
            .param("desc", p.description() == null ? null : p.description().strip())
            .param("topic", p.topicId())
            .param("diff", p.difficulty())
            .param("tier", p.accessTier())
            .param("est", p.estMinutes())
            .param("now", now())
            .param("id", id)
            .param("v", p.expectedVersion())
            .update();
    if (n == 0) {
      throw versionConflictOrMissing("catalog.courses", id, "Course");
    }
    return course(id);
  }

  /** draft -> published (needs a published lesson), published -> draft, any -> archived. */
  @Transactional
  CourseView setCourseStatus(Authentication actor, UUID id, String target) {
    UUID caller = directory.current(actor).id();
    var cur =
        jdbc.sql("select status, description from catalog.courses where id = :id for update")
            .param("id", id)
            .query((rs, i) -> new String[] {rs.getString(1), rs.getString(2)})
            .optional()
            .orElseThrow(() -> ApiException.notFound("Course not found."));
    String from = cur[0];
    if (from.equals(target)) {
      throw ApiException.conflict("The course is already " + target + ".");
    }
    if (target.equals("published")) {
      if (cur[1] == null || cur[1].isBlank()) {
        throw ApiException.conflict("Add a course description before publishing.");
      }
      long lessons =
          jdbc.sql(
                  "select count(*) from catalog.lessons where course_id = :c and status = 'published'")
              .param("c", id)
              .query(Long.class)
              .single();
      if (lessons == 0) {
        throw ApiException.conflict("Publish at least one lesson before publishing the course.");
      }
    }
    jdbc.sql(
            "update catalog.courses set status = :s, published_at = case when :s = 'published' then :now else published_at end, version = version + 1, updated_at = :now where id = :id")
        .param("s", target)
        .param("now", now())
        .param("id", id)
        .update();
    audit.record(
        actor,
        caller,
        AuditEvent.of(
                "course."
                    + (target.equals("published")
                        ? "published"
                        : target.equals("archived") ? "archived" : "unpublished"),
                "course",
                id.toString())
            .withChange(Map.of("status", from), Map.of("status", target)));
    events.publish(
        "catalog.course." + target,
        1,
        "course",
        id.toString(),
        Map.of("courseId", id, "status", target));
    return course(id);
  }

  // ================================================================= sections

  @Transactional
  CourseView createSection(UUID courseId, String title) {
    requireCourse(courseId);
    jdbc.sql(
            "insert into catalog.sections (id, course_id, title, position) values (:id, :c, :t, coalesce((select max(position) + 1 from catalog.sections where course_id = :c), 0))")
        .param("id", Ids.newId())
        .param("c", courseId)
        .param("t", title.strip())
        .update();
    touchCourse(courseId);
    return course(courseId);
  }

  @Transactional
  CourseView renameSection(UUID courseId, UUID sectionId, String title) {
    int n =
        jdbc.sql("update catalog.sections set title = :t where id = :s and course_id = :c")
            .param("t", title.strip())
            .param("s", sectionId)
            .param("c", courseId)
            .update();
    if (n == 0) {
      throw ApiException.notFound("Section not found.");
    }
    touchCourse(courseId);
    return course(courseId);
  }

  @Transactional
  CourseView deleteSection(Authentication actor, UUID courseId, UUID sectionId) {
    requireSection(courseId, sectionId);
    long lessons =
        jdbc.sql("select count(*) from catalog.lessons where section_id = :s")
            .param("s", sectionId)
            .query(Long.class)
            .single();
    if (lessons > 0) {
      throw ApiException.conflict("Move or delete the lessons of this section first.");
    }
    jdbc.sql("delete from catalog.sections where id = :s").param("s", sectionId).update();
    touchCourse(courseId);
    audit.record(
        actor,
        directory.current(actor).id(),
        AuditEvent.of("section.deleted", "course", courseId.toString())
            .withChange(Map.of("sectionId", sectionId.toString()), null));
    return course(courseId);
  }

  @Transactional
  CourseView reorderSections(UUID courseId, List<UUID> ids) {
    requireCourse(courseId);
    List<UUID> current =
        jdbc.sql("select id from catalog.sections where course_id = :c")
            .param("c", courseId)
            .query(UUID.class)
            .list();
    applyOrder("catalog.sections", "id", current, ids);
    touchCourse(courseId);
    return course(courseId);
  }

  // ================================================================= lessons

  @Transactional
  LessonView createLesson(UUID courseId, LessonCreate r) {
    String courseTier = requireCourseTier(courseId);
    requireSection(courseId, r.sectionId());
    UUID id = Ids.newId();
    try {
      jdbc.sql(
              """
              insert into catalog.lessons (id, course_id, section_id, slug, title, position, access_tier,
                                           free_preview, status, version, created_at, updated_at)
              values (:id, :c, :s, :slug, :title,
                      coalesce((select max(position) + 1 from catalog.lessons where section_id = :s), 0),
                      :tier, false, 'draft', 0, :now, :now)
              """)
          .param("id", id)
          .param("c", courseId)
          .param("s", r.sectionId())
          .param("slug", r.slug())
          .param("title", r.title().strip())
          .param("tier", r.accessTier() == null ? courseTier : r.accessTier())
          .param("now", now())
          .update();
    } catch (DuplicateKeyException e) {
      throw ApiException.conflict("A lesson with this slug already exists in the course.");
    }
    touchCourse(courseId);
    return lesson(courseId, id);
  }

  @Transactional(readOnly = true)
  LessonView lesson(UUID courseId, UUID lessonId) {
    var row =
        jdbc.sql(
                "select section_id, slug, title, position, access_tier, free_preview, est_read_min, status, version, published_at, (published_at is not null and updated_at > published_at) from catalog.lessons where id = :l and course_id = :c")
            .param("l", lessonId)
            .param("c", courseId)
            .query(
                (rs, i) ->
                    new Object[] {
                      rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getInt(4),
                      rs.getString(5), rs.getBoolean(6), (Integer) rs.getObject(7), rs.getString(8),
                      rs.getInt(9), ts(rs.getTimestamp(10)), rs.getBoolean(11)
                    })
            .optional()
            .orElseThrow(() -> ApiException.notFound("Lesson not found."));
    List<BlockView> blocks =
        jdbc.sql(
                "select type, schema_version, data::text from catalog.content_blocks where lesson_id = :l order by position")
            .param("l", lessonId)
            .query(
                (rs, i) -> new BlockView(rs.getString(1), rs.getInt(2), readMap(rs.getString(3))))
            .list();
    SourceView source =
        jdbc.sql(
                "select kind, license, attribution, source_url, reviewed_by, reviewed_at from catalog.content_sources where entity_type = 'lesson' and entity_id = :l")
            .param("l", lessonId)
            .query(
                (rs, i) ->
                    new SourceView(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getObject(5, UUID.class),
                        ts(rs.getTimestamp(6))))
            .optional()
            .orElse(null);
    return new LessonView(
        lessonId,
        courseId,
        (UUID) row[0],
        (String) row[1],
        (String) row[2],
        (Integer) row[3],
        (String) row[4],
        (Boolean) row[5],
        (Integer) row[6],
        (String) row[7],
        (Integer) row[8],
        (Instant) row[9],
        (Boolean) row[10],
        blocks,
        source);
  }

  @Transactional
  LessonView patchLesson(UUID courseId, UUID lessonId, LessonPatch p) {
    UUID targetSection = p.sectionId();
    if (targetSection != null) {
      requireSection(courseId, targetSection);
    }
    int n =
        jdbc.sql(
                """
                update catalog.lessons set
                  title = coalesce(:title, title), access_tier = coalesce(:tier, access_tier),
                  free_preview = coalesce(:fp, free_preview),
                  section_id = coalesce(cast(:sec as uuid), section_id),
                  position = case when cast(:sec as uuid) is not null and cast(:sec as uuid) <> section_id
                                  then coalesce((select max(position) + 1 from catalog.lessons where section_id = cast(:sec as uuid)), 0)
                                  else position end,
                  version = version + 1, updated_at = :now
                where id = :l and course_id = :c and version = :v
                """)
            .param("title", p.title() == null ? null : p.title().strip())
            .param("tier", p.accessTier())
            .param("fp", p.freePreview())
            .param("sec", targetSection)
            .param("now", now())
            .param("l", lessonId)
            .param("c", courseId)
            .param("v", p.expectedVersion())
            .update();
    if (n == 0) {
      throw lessonVersionConflictOrMissing(courseId, lessonId);
    }
    touchCourse(courseId);
    return lesson(courseId, lessonId);
  }

  @Transactional
  LessonView replaceBlocks(
      UUID courseId, UUID lessonId, List<BlockInput> input, int expectedVersion) {
    List<BlockValidator.Block> blocks =
        BlockValidator.validate(input.stream().map(b -> mapOf(b)).toList());
    int words = 0;
    for (var b : blocks) {
      for (Object v : b.data().values()) {
        if (v instanceof String s) {
          words += s.split("\\s+").length;
        }
      }
    }
    int minutes = Math.max(1, (int) Math.ceil(words / 200.0));
    int n =
        jdbc.sql(
                "update catalog.lessons set est_read_min = :m, version = version + 1, updated_at = :now where id = :l and course_id = :c and version = :v")
            .param("m", minutes)
            .param("now", now())
            .param("l", lessonId)
            .param("c", courseId)
            .param("v", expectedVersion)
            .update();
    if (n == 0) {
      throw lessonVersionConflictOrMissing(courseId, lessonId);
    }
    jdbc.sql("delete from catalog.content_blocks where lesson_id = :l")
        .param("l", lessonId)
        .update();
    int pos = 0;
    for (var b : blocks) {
      jdbc.sql(
              "insert into catalog.content_blocks (id, lesson_id, position, type, schema_version, data) values (:id, :l, :p, :t, 1, cast(:d as jsonb))")
          .param("id", Ids.newId())
          .param("l", lessonId)
          .param("p", pos++)
          .param("t", b.type())
          .param("d", json(b.data()))
          .update();
    }
    touchCourse(courseId);
    return lesson(courseId, lessonId);
  }

  @Transactional
  LessonView setSource(UUID courseId, UUID lessonId, SourceRequest r) {
    requireLesson(courseId, lessonId);
    boolean needsLicense = r.kind().equals("licensed") || r.kind().equals("open_license");
    if (needsLicense && (blank(r.license()) || blank(r.attribution()))) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "Licensed content needs a license and an attribution.");
    }
    // A changed source needs a fresh sign-off at the next publish.
    jdbc.sql(
            """
            insert into catalog.content_sources (id, entity_type, entity_id, kind, license, attribution, source_url, updated_at)
            values (:id, 'lesson', :l, :k, :lic, :att, :url, :now)
            on conflict (entity_type, entity_id) do update set
              kind = excluded.kind, license = excluded.license, attribution = excluded.attribution,
              source_url = excluded.source_url, reviewed_by = null, reviewed_at = null, updated_at = excluded.updated_at
            """)
        .param("id", Ids.newId())
        .param("l", lessonId)
        .param("k", r.kind())
        .param("lic", blankToNull(r.license()))
        .param("att", blankToNull(r.attribution()))
        .param("url", blankToNull(r.sourceUrl()))
        .param("now", now())
        .update();
    return lesson(courseId, lessonId);
  }

  /**
   * Publishing is the review sign-off (docs/20 §3): the publisher confirms originality, a content
   * source must exist, and the lesson must have valid content. A snapshot is taken: that is what
   * readers get.
   */
  @Transactional
  LessonView publishLesson(
      Authentication actor, UUID courseId, UUID lessonId, boolean originalityConfirmed) {
    UUID caller = directory.current(actor).id();
    var cur =
        jdbc.sql(
                "select title, version, status from catalog.lessons where id = :l and course_id = :c for update")
            .param("l", lessonId)
            .param("c", courseId)
            .query((rs, i) -> new Object[] {rs.getString(1), rs.getInt(2), rs.getString(3)})
            .optional()
            .orElseThrow(() -> ApiException.notFound("Lesson not found."));
    if (!originalityConfirmed) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED,
          "Confirm that the content is original or properly licensed.");
    }
    long sources =
        jdbc.sql(
                "select count(*) from catalog.content_sources where entity_type = 'lesson' and entity_id = :l")
            .param("l", lessonId)
            .query(Long.class)
            .single();
    if (sources == 0) {
      throw ApiException.conflict(
          "Record the content source (original, licensed, ...) before publishing.");
    }
    LessonView draft = lesson(courseId, lessonId);
    if (draft.blocks().isEmpty()) {
      throw ApiException.conflict("A lesson needs at least one content block before publishing.");
    }
    // Re-validate what is stored (defence in depth: the snapshot is what learners receive).
    BlockValidator.validate(
        draft.blocks().stream().map(b -> mapOf(new BlockInput(b.type(), b.data()))).toList());

    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("title", draft.title());
    snapshot.put("estReadMin", draft.estReadMin());
    snapshot.put(
        "blocks",
        draft.blocks().stream()
            .map(
                b -> Map.of("type", b.type(), "schemaVersion", b.schemaVersion(), "data", b.data()))
            .toList());
    Timestamp ts = now();
    jdbc.sql(
            "update catalog.lessons set status = 'published', published_snapshot = cast(:s as jsonb), published_at = :now, updated_at = :now where id = :l")
        .param("s", json(snapshot))
        .param("now", ts)
        .param("l", lessonId)
        .update();
    jdbc.sql(
            "update catalog.content_sources set reviewed_by = :u, reviewed_at = :now where entity_type = 'lesson' and entity_id = :l")
        .param("u", caller)
        .param("now", ts)
        .param("l", lessonId)
        .update();
    touchCourse(courseId);
    audit.record(
        actor,
        caller,
        AuditEvent.of("lesson.published", "lesson", lessonId.toString())
            .withChange(
                Map.of("status", (String) cur[2]),
                Map.of("status", "published", "version", (Integer) cur[1])));
    events.publish(
        "catalog.lesson.published",
        1,
        "lesson",
        lessonId.toString(),
        Map.of("lessonId", lessonId, "courseId", courseId));
    return lesson(courseId, lessonId);
  }

  @Transactional
  LessonView unpublishLesson(Authentication actor, UUID courseId, UUID lessonId) {
    UUID caller = directory.current(actor).id();
    String status =
        jdbc.sql("select status from catalog.lessons where id = :l and course_id = :c for update")
            .param("l", lessonId)
            .param("c", courseId)
            .query(String.class)
            .optional()
            .orElseThrow(() -> ApiException.notFound("Lesson not found."));
    if (!status.equals("published")) {
      throw ApiException.conflict("The lesson is not published.");
    }
    jdbc.sql(
            "update catalog.lessons set status = 'draft', published_snapshot = null, published_at = null, updated_at = :now where id = :l")
        .param("now", now())
        .param("l", lessonId)
        .update();
    touchCourse(courseId);
    audit.record(
        actor,
        caller,
        AuditEvent.of("lesson.unpublished", "lesson", lessonId.toString())
            .withChange(Map.of("status", "published"), Map.of("status", "draft")));
    events.publish(
        "catalog.lesson.unpublished",
        1,
        "lesson",
        lessonId.toString(),
        Map.of("lessonId", lessonId, "courseId", courseId));
    return lesson(courseId, lessonId);
  }

  @Transactional
  void deleteLesson(Authentication actor, UUID courseId, UUID lessonId) {
    String status =
        jdbc.sql("select status from catalog.lessons where id = :l and course_id = :c for update")
            .param("l", lessonId)
            .param("c", courseId)
            .query(String.class)
            .optional()
            .orElseThrow(() -> ApiException.notFound("Lesson not found."));
    if (!status.equals("draft")) {
      throw ApiException.conflict("Unpublish the lesson before deleting it.");
    }
    jdbc.sql("delete from catalog.content_sources where entity_type = 'lesson' and entity_id = :l")
        .param("l", lessonId)
        .update();
    jdbc.sql("delete from catalog.lessons where id = :l").param("l", lessonId).update();
    touchCourse(courseId);
    audit.record(
        actor,
        directory.current(actor).id(),
        AuditEvent.of("lesson.deleted", "lesson", lessonId.toString()));
  }

  @Transactional
  CourseView reorderLessons(UUID courseId, UUID sectionId, List<UUID> ids) {
    requireSection(courseId, sectionId);
    List<UUID> current =
        jdbc.sql("select id from catalog.lessons where section_id = :s")
            .param("s", sectionId)
            .query(UUID.class)
            .list();
    applyOrder("catalog.lessons", "id", current, ids);
    touchCourse(courseId);
    return course(courseId);
  }

  // ================================================================= helpers

  private void applyOrder(String table, String idColumn, List<UUID> current, List<UUID> wanted) {
    Set<UUID> a = new HashSet<>(current);
    if (wanted.size() != current.size() || !a.equals(new HashSet<>(wanted))) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "The order must list every item exactly once.");
    }
    int pos = 0;
    for (UUID id :
        wanted) { // unique(position) constraints are deferred, so any intermediate state is fine
      jdbc.sql("update " + table + " set position = :p where " + idColumn + " = :id")
          .param("p", pos++)
          .param("id", id)
          .update();
    }
  }

  private void touchCourse(UUID courseId) {
    jdbc.sql("update catalog.courses set updated_at = :now where id = :c")
        .param("now", now())
        .param("c", courseId)
        .update();
  }

  private void requireTopic(UUID topicId) {
    if (topicId != null
        && jdbc.sql("select count(*) from catalog.topics where id = :t")
                .param("t", topicId)
                .query(Long.class)
                .single()
            == 0) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown topic.");
    }
  }

  private void requireCourse(UUID id) {
    requireCourseTier(id);
  }

  private String requireCourseTier(UUID id) {
    return jdbc.sql("select access_tier from catalog.courses where id = :id")
        .param("id", id)
        .query(String.class)
        .optional()
        .orElseThrow(() -> ApiException.notFound("Course not found."));
  }

  private void requireSection(UUID courseId, UUID sectionId) {
    if (jdbc.sql("select count(*) from catalog.sections where id = :s and course_id = :c")
            .param("s", sectionId)
            .param("c", courseId)
            .query(Long.class)
            .single()
        == 0) {
      throw ApiException.notFound("Section not found.");
    }
  }

  private void requireLesson(UUID courseId, UUID lessonId) {
    if (jdbc.sql("select count(*) from catalog.lessons where id = :l and course_id = :c")
            .param("l", lessonId)
            .param("c", courseId)
            .query(Long.class)
            .single()
        == 0) {
      throw ApiException.notFound("Lesson not found.");
    }
  }

  private ApiException versionConflictOrMissing(String table, UUID id, String what) {
    boolean exists =
        jdbc.sql("select count(*) from " + table + " where id = :id")
                .param("id", id)
                .query(Long.class)
                .single()
            > 0;
    return exists
        ? ApiException.conflict(what + " was changed by someone else. Reload and try again.")
        : ApiException.notFound(what + " not found.");
  }

  private ApiException lessonVersionConflictOrMissing(UUID courseId, UUID lessonId) {
    boolean exists =
        jdbc.sql("select count(*) from catalog.lessons where id = :l and course_id = :c")
                .param("l", lessonId)
                .param("c", courseId)
                .query(Long.class)
                .single()
            > 0;
    return exists
        ? ApiException.conflict("The lesson was changed by someone else. Reload and try again.")
        : ApiException.notFound("Lesson not found.");
  }

  private static Map<String, Object> mapOf(BlockInput b) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", b.type());
    m.put("data", b.data());
    return m;
  }

  private static Instant ts(Timestamp t) {
    return t == null ? null : t.toInstant();
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private static String blankToNull(String s) {
    return blank(s) ? null : s.strip();
  }

  private String json(Object o) {
    return mapper.writeValueAsString(o);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readMap(String s) {
    return mapper.readValue(s, Map.class);
  }
}
