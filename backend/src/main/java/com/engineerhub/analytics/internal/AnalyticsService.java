package com.engineerhub.analytics.internal;

import com.engineerhub.analytics.internal.StreakCalculator.Streak;
import com.engineerhub.identity.UserDirectory;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns domain events into activity statistics and serves them. Writes happen only here (called by
 * the event consumer); reads serve the dashboard.
 */
@Service
class AnalyticsService {

  record DayCount(LocalDate day, int lessonsCompleted) {}

  record Dashboard(
      int currentStreak,
      int longestStreak,
      LocalDate lastActiveDay,
      boolean activeToday,
      LocalDate today,
      String timezone,
      long lessonsCompleted,
      long coursesCompleted,
      List<DayCount> activity) {}

  private final JdbcClient jdbc;
  private final Clock clock;
  private final UserDirectory directory;

  AnalyticsService(JdbcClient jdbc, Clock clock, UserDirectory directory) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.directory = directory;
  }

  // ------------------------------------------------------------------ writes (event consumer)

  /**
   * @return true if this was the user's first completion of the lesson and was counted
   */
  @Transactional
  boolean onLessonCompleted(UUID userId, UUID lessonId, UUID courseId, Instant at) {
    ZoneId zone = directory.zoneOf(userId);
    LocalDate day = StreakCalculator.dayOf(at, zone);
    int inserted =
        jdbc.sql(
                "insert into analytics.lesson_completions (user_id, lesson_id, course_id, completed_at, day) values (:u, :l, :c, :at, :d) on conflict do nothing")
            .param("u", userId)
            .param("l", lessonId)
            .param("c", courseId)
            .param("at", Timestamp.from(at))
            .param("d", Date.valueOf(day))
            .update();
    if (inserted == 0) {
      return false; // un-mark / re-complete / replay: counted once per lesson
    }
    jdbc.sql(
            "insert into analytics.daily_user_stats (user_id, day, lessons_completed) values (:u, :d, 1) on conflict (user_id, day) do update set lessons_completed = analytics.daily_user_stats.lessons_completed + 1")
        .param("u", userId)
        .param("d", Date.valueOf(day))
        .update();

    // row lock: two completions racing for the same user must not both read the old streak
    jdbc.sql(
            "insert into analytics.user_streaks (user_id, updated_at) values (:u, :now) on conflict do nothing")
        .param("u", userId)
        .param("now", Timestamp.from(clock.instant()))
        .update();
    Streak current =
        jdbc.sql(
                "select current_streak, longest_streak, last_active_day from analytics.user_streaks where user_id = :u for update")
            .param("u", userId)
            .query(
                (rs, i) ->
                    new Streak(
                        rs.getInt(1),
                        rs.getInt(2),
                        rs.getDate(3) == null ? null : rs.getDate(3).toLocalDate()))
            .single();
    Streak next = StreakCalculator.apply(current, day);
    if (!next.equals(current)) {
      jdbc.sql(
              "update analytics.user_streaks set current_streak = :c, longest_streak = :l, last_active_day = :d, updated_at = :now where user_id = :u")
          .param("c", next.current())
          .param("l", next.longest())
          .param("d", next.lastActiveDay() == null ? null : Date.valueOf(next.lastActiveDay()))
          .param("now", Timestamp.from(clock.instant()))
          .param("u", userId)
          .update();
    }
    return true;
  }

  @Transactional
  boolean onCourseCompleted(UUID userId, UUID courseId, Instant at) {
    return jdbc.sql(
                "insert into analytics.completed_courses (user_id, course_id, completed_at) values (:u, :c, :at) on conflict do nothing")
            .param("u", userId)
            .param("c", courseId)
            .param("at", Timestamp.from(at))
            .update()
        > 0;
  }

  // ------------------------------------------------------------------ reads

  @Transactional(readOnly = true)
  Dashboard dashboard(UUID userId, int days) {
    ZoneId zone = directory.zoneOf(userId);
    LocalDate today = clock.instant().atZone(zone).toLocalDate();
    Streak streak =
        jdbc.sql(
                "select current_streak, longest_streak, last_active_day from analytics.user_streaks where user_id = :u")
            .param("u", userId)
            .query(
                (rs, i) ->
                    new Streak(
                        rs.getInt(1),
                        rs.getInt(2),
                        rs.getDate(3) == null ? null : rs.getDate(3).toLocalDate()))
            .optional()
            .orElse(Streak.NONE);
    long lessons =
        jdbc.sql("select count(*) from analytics.lesson_completions where user_id = :u")
            .param("u", userId)
            .query(Long.class)
            .single();
    long courses =
        jdbc.sql("select count(*) from analytics.completed_courses where user_id = :u")
            .param("u", userId)
            .query(Long.class)
            .single();
    List<DayCount> activity = new ArrayList<>();
    jdbc.sql(
            "select day, lessons_completed from analytics.daily_user_stats where user_id = :u and day > :from order by day")
        .param("u", userId)
        .param("from", Date.valueOf(today.minusDays(days)))
        .query((rs, i) -> activity.add(new DayCount(rs.getDate(1).toLocalDate(), rs.getInt(2))))
        .list();
    return new Dashboard(
        StreakCalculator.effectiveCurrent(streak, today),
        streak.longest(),
        streak.lastActiveDay(),
        StreakCalculator.activeToday(streak, today),
        today,
        zone.getId(),
        lessons,
        courses,
        activity);
  }
}
