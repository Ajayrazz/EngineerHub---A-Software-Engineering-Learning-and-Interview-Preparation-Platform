package com.engineerhub.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoverageEndTest {

  private static final Instant NOW = Instant.parse("2026-01-10T00:00:00Z");

  private static Instant[] r(String from, String to) {
    return new Instant[] {Instant.parse(from), Instant.parse(to)};
  }

  @Test
  void noPeriodsMeansNoCoverage() {
    assertThat(BillingService.coverageEnd(NOW, List.of())).isNull();
  }

  @Test
  void aFutureStartingPeriodAloneIsNotCoverage() {
    assertThat(
            BillingService.coverageEnd(
                NOW, List.<Instant[]>of(r("2026-01-11T00:00:00Z", "2026-02-11T00:00:00Z"))))
        .isNull();
  }

  @Test
  void touchingPeriodsChainAndTheStartBoundaryIsInclusive() {
    assertThat(
            BillingService.coverageEnd(
                NOW,
                List.of(
                    r("2026-01-09T00:00:00Z", "2026-01-12T00:00:00Z"),
                    r("2026-01-12T00:00:00Z", "2026-02-12T00:00:00Z"))))
        .isEqualTo(Instant.parse("2026-02-12T00:00:00Z"));
  }

  @Test
  void aGapStopsTheChain() {
    assertThat(
            BillingService.coverageEnd(
                NOW,
                List.of(
                    r("2026-01-09T00:00:00Z", "2026-01-12T00:00:00Z"),
                    r("2026-01-13T00:00:00Z", "2026-02-12T00:00:00Z"))))
        .isEqualTo(Instant.parse("2026-01-12T00:00:00Z"));
  }

  @Test
  void aPeriodEndingExactlyNowDoesNotCount() {
    assertThat(
            BillingService.coverageEnd(
                NOW, List.<Instant[]>of(r("2026-01-01T00:00:00Z", "2026-01-10T00:00:00Z"))))
        .isNull();
  }

  @Test
  void overlappingPeriodsTakeTheLatestEnd() {
    assertThat(
            BillingService.coverageEnd(
                NOW,
                List.of(
                    r("2026-01-01T00:00:00Z", "2026-03-01T00:00:00Z"),
                    r("2026-01-05T00:00:00Z", "2026-02-01T00:00:00Z"))))
        .isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
  }
}
