package com.engineerhub.billing.internal;

import java.time.LocalDate;

/**
 * Tax arithmetic on integer minor units only (docs/21 §5). Prices are tax-INCLUSIVE, so tax is
 * extracted from the final amount: {@code tax = round_half_up(total * rate / (1 + rate))} and
 * {@code net = total - tax}. The sum always equals the total exactly, which is what the customer
 * paid. (The rounding rule is a documented default until the accountant confirms it: Pending
 * Decision P-4.)
 */
final class TaxMath {

  private TaxMath() {}

  record Split(long netMinor, long taxMinor) {}

  /** {@code rateBps} is the rate in basis points (1800 = 18.00 %). */
  static Split extractFromInclusive(long totalMinor, int rateBps) {
    if (totalMinor < 0 || rateBps < 0) {
      throw new IllegalArgumentException("amount and rate must not be negative");
    }
    long denominator = 10_000L + rateBps;
    // round half up of totalMinor * rateBps / denominator, without floating point
    long tax =
        Math.floorDiv(
            Math.multiplyExact(totalMinor, (long) rateBps) * 2 + denominator, 2 * denominator);
    return new Split(totalMinor - tax, tax);
  }

  /**
   * The Indian financial year of a date as {@code 2026-27} (April to March). This is a calendar
   * fact used to number documents; whether it suits the owner's accounting is part of P-4.
   */
  static String financialYear(LocalDate date) {
    int start = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
    return "%d-%02d".formatted(start, (start + 1) % 100);
  }
}
