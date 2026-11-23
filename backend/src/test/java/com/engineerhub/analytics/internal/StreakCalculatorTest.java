package com.engineerhub.analytics.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.engineerhub.analytics.internal.StreakCalculator.Streak;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class StreakCalculatorTest {

  private static final LocalDate D1 = LocalDate.of(2026, 3, 10);

  @Test
  void theFirstActivityStartsAStreakOfOne() {
    assertThat(StreakCalculator.apply(Streak.NONE, D1)).isEqualTo(new Streak(1, 1, D1));
  }

  @Test
  void severalEventsOnTheSameDayCountOnce() {
    var s = StreakCalculator.apply(Streak.NONE, D1);
    assertThat(StreakCalculator.apply(s, D1)).isEqualTo(s);
    // and an established streak is neither grown nor reset by more activity the same day
    var established = new Streak(3, 5, D1);
    assertThat(StreakCalculator.apply(established, D1)).isEqualTo(established);
  }

  @Test
  void consecutiveDaysGrowTheStreakAndTrackTheLongest() {
    var s = StreakCalculator.apply(Streak.NONE, D1);
    s = StreakCalculator.apply(s, D1.plusDays(1));
    s = StreakCalculator.apply(s, D1.plusDays(2));
    assertThat(s).isEqualTo(new Streak(3, 3, D1.plusDays(2)));
  }

  @Test
  void aGapRestartsTheStreakButKeepsTheLongest() {
    var s = new Streak(3, 3, D1.plusDays(2));
    var after = StreakCalculator.apply(s, D1.plusDays(4)); // skipped one day
    assertThat(after).isEqualTo(new Streak(1, 3, D1.plusDays(4)));
    assertThat(StreakCalculator.apply(after, D1.plusDays(5)).longest()).isEqualTo(3);
  }

  @Test
  void aLateOrOutOfOrderEventNeverChangesTheStreak() {
    var s = new Streak(5, 5, D1.plusDays(10));
    assertThat(StreakCalculator.apply(s, D1)).isEqualTo(s);
    assertThat(StreakCalculator.apply(s, D1.plusDays(9))).isEqualTo(s);
  }

  @Test
  void aStreakThatEndedBeforeYesterdayIsShownAsZeroUntilNewActivity() {
    var s = new Streak(4, 4, D1);
    assertThat(StreakCalculator.effectiveCurrent(s, D1)).isEqualTo(4); // active today
    assertThat(StreakCalculator.effectiveCurrent(s, D1.plusDays(1)))
        .isEqualTo(4); // still alive: today is not over
    assertThat(StreakCalculator.effectiveCurrent(s, D1.plusDays(2))).isZero(); // missed a whole day
    assertThat(StreakCalculator.effectiveCurrent(Streak.NONE, D1)).isZero();
    assertThat(StreakCalculator.activeToday(s, D1)).isTrue();
    assertThat(StreakCalculator.activeToday(s, D1.plusDays(1))).isFalse();
  }

  @Test
  void theDayIsTheUsersDayNotUtc() {
    Instant lateEveningUtc = Instant.parse("2026-03-10T20:00:00Z");
    assertThat(StreakCalculator.dayOf(lateEveningUtc, ZoneId.of("UTC")))
        .isEqualTo(LocalDate.of(2026, 3, 10));
    assertThat(StreakCalculator.dayOf(lateEveningUtc, ZoneId.of("Asia/Kolkata")))
        .isEqualTo(LocalDate.of(2026, 3, 11)); // 01:30 next day
    assertThat(StreakCalculator.dayOf(lateEveningUtc, ZoneId.of("America/Los_Angeles")))
        .isEqualTo(LocalDate.of(2026, 3, 10)); // 13:00
    assertThat(
            StreakCalculator.dayOf(
                Instant.parse("2026-03-10T03:00:00Z"), ZoneId.of("America/Los_Angeles")))
        .isEqualTo(LocalDate.of(2026, 3, 9));
  }
}
