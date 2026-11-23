package com.engineerhub.analytics.internal;

import java.time.LocalDate;
import java.time.ZoneId;

/** Pure streak arithmetic (no I/O), so every day-boundary rule can be tested directly. */
final class StreakCalculator {

  /**
   * @param lastActiveDay null before the first activity
   */
  record Streak(int current, int longest, LocalDate lastActiveDay) {
    static final Streak NONE = new Streak(0, 0, null);
  }

  private StreakCalculator() {}

  /**
   * Applies one activity on {@code day} (the user's local day).
   *
   * <ul>
   *   <li>same day as the last activity: unchanged (a streak counts days, not events);
   *   <li>the day after: the streak grows;
   *   <li>any later day (gap): a new streak of 1;
   *   <li>an earlier day (a late or out-of-order event): the streak is left alone, the daily count
   *       is still recorded by the caller. Repairing history is deliberately not attempted.
   * </ul>
   */
  static Streak apply(Streak s, LocalDate day) {
    LocalDate last = s.lastActiveDay();
    if (last == null) {
      return new Streak(1, Math.max(1, s.longest()), day);
    }
    if (day.isBefore(last) || day.isEqual(last)) {
      return s;
    }
    int current = day.isEqual(last.plusDays(1)) ? s.current() + 1 : 1;
    return new Streak(current, Math.max(s.longest(), current), day);
  }

  /**
   * The streak as it should be SHOWN today: a streak whose last day is before yesterday has ended.
   */
  static int effectiveCurrent(Streak s, LocalDate today) {
    LocalDate last = s.lastActiveDay();
    if (last == null || last.isBefore(today.minusDays(1))) {
      return 0;
    }
    return s.current();
  }

  static boolean activeToday(Streak s, LocalDate today) {
    return s.lastActiveDay() != null && s.lastActiveDay().isEqual(today);
  }

  static LocalDate dayOf(java.time.Instant instant, ZoneId zone) {
    return instant.atZone(zone).toLocalDate();
  }
}
