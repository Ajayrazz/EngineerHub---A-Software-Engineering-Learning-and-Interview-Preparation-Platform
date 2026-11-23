package com.engineerhub.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TaxMathTest {

  @Test
  void aKnownExtractionRoundsTheTaxHalfUpAndKeepsTheTotalExact() {
    // 29900 at 18.00 %: 29900 * 1800 / 11800 = 4561.0169... -> 4561 (the rate is only an example)
    var s = TaxMath.extractFromInclusive(29900, 1800);
    assertThat(s.taxMinor()).isEqualTo(4561);
    assertThat(s.netMinor()).isEqualTo(25339);
  }

  @Test
  void exactHalvesRoundUpAndJustUnderStaysDown() {
    // 1 * 10000 / 20000 = 0.5 -> 1
    assertThat(TaxMath.extractFromInclusive(1, 10_000).taxMinor()).isEqualTo(1);
    // 1 * 5000 / 15000 = 0.333 -> 0
    assertThat(TaxMath.extractFromInclusive(1, 5_000).taxMinor()).isZero();
    // 3 * 5000 / 15000 = 1.0 -> 1
    assertThat(TaxMath.extractFromInclusive(3, 5_000).taxMinor()).isEqualTo(1);
    // 5 * 500 / 10500 = 0.238 -> 0 ; 21 * 500 / 10500 = 1.0 -> 1
    assertThat(TaxMath.extractFromInclusive(5, 500).taxMinor()).isZero();
    assertThat(TaxMath.extractFromInclusive(21, 500).taxMinor()).isEqualTo(1);
  }

  @Test
  void propertyAgainstAnIndependentBigDecimalReference() {
    Random rnd = new Random(20261124);
    for (int i = 0; i < 100_000; i++) {
      long total = 100 + (long) (rnd.nextDouble() * 100_000_000);
      int bps = 1 + rnd.nextInt(5000);
      var s = TaxMath.extractFromInclusive(total, bps);
      BigDecimal t = BigDecimal.valueOf(total);
      BigDecimal rate = BigDecimal.valueOf(bps).divide(BigDecimal.valueOf(10_000));
      long expectedTax =
          t.multiply(rate)
              .divide(BigDecimal.ONE.add(rate), 0, RoundingMode.HALF_UP)
              .longValueExact();
      assertThat(s.taxMinor()).as("total=%d bps=%d", total, bps).isEqualTo(expectedTax);
      assertThat(s.netMinor() + s.taxMinor()).isEqualTo(total);
      assertThat(s.taxMinor()).isBetween(0L, total);
    }
  }

  @Test
  void zeroRateMeansNoTaxAndInvalidInputIsRefused() {
    assertThat(TaxMath.extractFromInclusive(29900, 0).taxMinor()).isZero();
    assertThatThrownBy(() -> TaxMath.extractFromInclusive(-1, 100))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TaxMath.extractFromInclusive(100, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theFinancialYearRunsFromAprilToMarch() {
    assertThat(TaxMath.financialYear(LocalDate.of(2026, 3, 31))).isEqualTo("2025-26");
    assertThat(TaxMath.financialYear(LocalDate.of(2026, 4, 1))).isEqualTo("2026-27");
    assertThat(TaxMath.financialYear(LocalDate.of(2026, 12, 31))).isEqualTo("2026-27");
    assertThat(TaxMath.financialYear(LocalDate.of(2027, 1, 1))).isEqualTo("2026-27");
    assertThat(TaxMath.financialYear(LocalDate.of(2099, 12, 31))).isEqualTo("2099-00");
  }
}
