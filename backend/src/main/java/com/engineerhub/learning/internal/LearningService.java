package com.engineerhub.learning.internal;

import com.engineerhub.billing.EntitlementService;
import com.engineerhub.catalog.CatalogApi;
import com.engineerhub.catalog.CatalogApi.PublishedLesson;
import com.engineerhub.identity.UserDirectory;
import com.engineerhub.learning.internal.LearningDtos.CourseProgress;
import com.engineerhub.learning.internal.LearningDtos.EnrollmentView;
import com.engineerhub.learning.internal.LearningDtos.ResumeTarget;
import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import com.engineerhub.shared.id.Ids;
import com.engineerhub.shared.messaging.OutboxPublisher;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enrollment and progress. Every row belongs to the caller: the user id always comes from the
 * security context, no endpoint takes a user id. Content facts come from the catalog API (published
 * only), and a lesson can be marked only if the caller may read it (same gating as the reader).
 */
@Service
class LearningService {

  private final JdbcClient jdbc;
  private final Clock clock;
  private final CatalogApi catalog;
  private final EntitlementService entitlements;
  private final UserDirectory directory;
  private final OutboxPublisher events;

  LearningService(
      JdbcClient jdbc,
      Clock clock,
      CatalogApi catalog,
      EntitlementService entitlements,
      UserDirectory directory,
      OutboxPublisher events) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.catalog = catalog;
    this.entitlements = entitlements;
    this.directory = directory;
    this.events = events;
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }

  // ------------------------------------------------------------------ enrollment

  @Transactional
  CourseProgress enroll(Authentication auth, UUID courseId) {
    UUID user = directory.current(auth).id();
    catalog.publishedCourse(courseId).orElseThrow(() -> ApiException.notFound("Course not found."));
    ensureEnrolled(user, courseId);
    return progress(user, courseId);
  }

  private void ensureEnrolled(UUID user, UUID courseId) {
    jdbc.sql(
            "insert into learning.enrollments (id, user_id, course_id, enrolled_at) values (:id, :u, :c, :now) on conflict (user_id, course_id) do nothing")
        .param("id", Ids.newId())
        .param("u", user)
        .param("c", courseId)
        .param("now", now())
        .update();
  }

  // ------------------------------------------------------------------ progress

  @Transactional
  CourseProgress update(Authentication auth, UUID lessonId, String status, Integer scrollPct) {
    UUID user = directory.current(auth).id();
    PublishedLesson lesson =
        catalog
            .publishedLesson(lessonId)
            .orElseThrow(() -> ApiException.notFound("Lesson not found."));
    boolean open = lesson.accessTier().equals("free") || lesson.freePreview();
    if (!open && !entitlements.canReadPremium(auth, "course", lesson.courseId())) {
      throw new ApiException(
          ErrorCode.ENTITLEMENT_REQUIRED, "This lesson is part of the premium content.");
    }
    ensureEnrolled(user, lesson.courseId());

    Timestamp ts = now();
    boolean completing = status.equals("completed");
    // completed_at keeps its first value; going back to in_progress clears it
    Boolean wasCompleted =
        jdbc.sql(
                "select status = 'completed' from learning.lesson_progress where user_id = :u and lesson_id = :l for update")
            .param("u", user)
            .param("l", lessonId)
            .query(Boolean.class)
            .optional()
            .orElse(null);
    jdbc.sql(
            """
            insert into learning.lesson_progress (user_id, lesson_id, course_id, status, completed_at, scroll_pct, updated_at)
            values (:u, :l, :c, :s, case when cast(:done as boolean) then cast(:now as timestamptz) else null end, cast(:pct as smallint), :now)
            on conflict (user_id, lesson_id) do update set
              status = excluded.status,
              completed_at = case when excluded.status = 'completed' then coalesce(learning.lesson_progress.completed_at, excluded.completed_at) else null end,
              scroll_pct = case when excluded.status = 'completed' then 100 else excluded.scroll_pct end,
              updated_at = excluded.updated_at
            """)
        .param("u", user)
        .param("l", lessonId)
        .param("c", lesson.courseId())
        .param("s", status)
        .param("done", completing)
        .param("pct", completing ? 100 : (scrollPct == null ? 0 : scrollPct))
        .param("now", ts)
        .update();
    jdbc.sql(
            "update learning.enrollments set last_lesson_id = :l, last_activity_at = :now where user_id = :u and course_id = :c")
        .param("l", lessonId)
        .param("now", ts)
        .param("u", user)
        .param("c", lesson.courseId())
        .update();

    if (completing && !Boolean.TRUE.equals(wasCompleted)) {
      events.publish(
          "learning.lesson.completed",
          1,
          "user",
          user.toString(),
          Map.of("userId", user, "lessonId", lessonId, "courseId", lesson.courseId()));
    }
    CourseProgress p = progress(user, lesson.courseId());
    syncCourseCompletion(user, lesson.courseId(), p.completed(), ts);
    return p;
  }

  private void syncCourseCompletion(UUID user, UUID courseId, boolean completed, Timestamp ts) {
    Boolean was =
        jdbc.sql(
                "select completed_at is not null from learning.enrollments where user_id = :u and course_id = :c for update")
            .param("u", user)
            .param("c", courseId)
            .query(Boolean.class)
            .optional()
            .orElse(false);
    if (completed && !was) {
      jdbc.sql(
              "update learning.enrollments set completed_at = :now where user_id = :u and course_id = :c")
          .param("now", ts)
          .param("u", user)
          .param("c", courseId)
          .update();
      events.publish(
          "learning.course.completed",
          1,
          "user",
          user.toString(),
          Map.of("userId", user, "courseId", courseId));
    } else if (!completed && was) {
      jdbc.sql(
              "update learning.enrollments set completed_at = null where user_id = :u and course_id = :c")
          .param("u", user)
          .param("c", courseId)
          .update();
    }
  }

  // not read-only: resolving the caller may provision the local user on first use
  @Transactional
  CourseProgress progress(Authentication auth, UUID courseId) {
    UUID user = directory.current(auth).id();
    catalog.publishedCourse(courseId).orElseThrow(() -> ApiException.notFound("Course not found."));
    return progress(user, courseId);
  }

  private CourseProgress progress(UUID user, UUID courseId) {
    List<PublishedLesson> lessons = catalog.publishedLessons(courseId);
    Set<UUID> published = new HashSet<>();
    lessons.forEach(l -> published.add(l.id()));
    List<UUID> done = new ArrayList<>();
    jdbc.sql(
            "select lesson_id from learning.lesson_progress where user_id = :u and course_id = :c and status = 'completed'")
        .param("u", user)
        .param("c", courseId)
        .query(UUID.class)
        .list()
        .forEach(
            id -> {
              if (published.contains(id)) {
                done.add(id); // lessons unpublished since do not count
              }
            });
    var enrollment =
        jdbc.sql(
                "select enrolled_at, last_lesson_id from learning.enrollments where user_id = :u and course_id = :c")
            .param("u", user)
            .param("c", courseId)
            .query(
                (rs, i) ->
                    new Object[] {rs.getTimestamp(1).toInstant(), rs.getObject(2, UUID.class)})
            .optional();
    int total = lessons.size();
    int completed = done.size();
    int percent = total == 0 ? 0 : (int) Math.floor(completed * 100.0 / total);

    ResumeTarget resume = null;
    if (enrollment.isPresent() && total > 0) {
      UUID last = (UUID) enrollment.get()[1];
      PublishedLesson target = null;
      if (last != null && published.contains(last)) {
        var lastLesson = lessons.stream().filter(l -> l.id().equals(last)).findFirst().orElse(null);
        // resume where they stopped, unless that lesson is done: then the next unfinished one
        target = lastLesson != null && !done.contains(last) ? lastLesson : null;
      }
      if (target == null) {
        target =
            lessons.stream().filter(l -> !done.contains(l.id())).findFirst().orElse(lessons.get(0));
      }
      resume = new ResumeTarget(target.id(), target.slug(), target.title());
    }
    return new CourseProgress(
        courseId,
        enrollment.isPresent(),
        total,
        completed,
        percent,
        total > 0 && completed == total,
        done,
        resume,
        enrollment.map(e -> (Instant) e[0]).orElse(null));
  }

  // not read-only: resolving the caller may provision the local user on first use
  @Transactional
  List<EnrollmentView> enrollments(Authentication auth) {
    UUID user = directory.current(auth).id();
    var rows =
        jdbc.sql(
                "select course_id, enrolled_at, last_activity_at from learning.enrollments where user_id = :u order by coalesce(last_activity_at, enrolled_at) desc limit 200")
            .param("u", user)
            .query(
                (rs, i) ->
                    new Object[] {
                      rs.getObject(1, UUID.class),
                      rs.getTimestamp(2).toInstant(),
                      rs.getTimestamp(3) == null ? null : rs.getTimestamp(3).toInstant()
                    })
            .list();
    Map<UUID, CatalogApi.PublishedCourse> courses =
        catalog.publishedCourses(rows.stream().map(r -> (UUID) r[0]).toList());
    List<EnrollmentView> out = new ArrayList<>();
    for (Object[] r : rows) {
      var c = courses.get((UUID) r[0]);
      if (c == null) {
        continue; // course no longer published
      }
      CourseProgress p = progress(user, c.id());
      out.add(
          new EnrollmentView(
              c.id(),
              c.slug(),
              c.title(),
              (Instant) r[1],
              (Instant) r[2],
              p.totalLessons(),
              p.completedLessons(),
              p.percent(),
              p.completed()));
    }
    return out;
  }
}
