package com.engineerhub.shared.id;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * Identifier factory (ADR-007): time-ordered UUIDv7 for domain entities (RFC 9562 layout: 48-bit
 * Unix millisecond timestamp, version 7, 74 random bits). Time ordering keeps B-tree index inserts
 * local; the 74 random bits keep ids unguessable enough to be exposed publicly (still never a
 * substitute for authorization checks).
 */
public final class Ids {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final long MAX_MILLIS = (1L << 48) - 1;

  private Ids() {}

  /** A new UUIDv7 stamped with the current time. */
  public static UUID newId() {
    return newId(System.currentTimeMillis());
  }

  /** A new UUIDv7 stamped with the given epoch milliseconds (exposed for deterministic tests). */
  public static UUID newId(long epochMillis) {
    if (epochMillis < 0 || epochMillis > MAX_MILLIS) {
      throw new IllegalArgumentException("epochMillis out of 48-bit range: " + epochMillis);
    }
    long randA = RANDOM.nextLong() & 0x0FFFL; // 12 random bits
    long msb = (epochMillis << 16) | 0x7000L | randA; // timestamp | version 7 | rand_a
    long lsb =
        (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L; // variant 10 | rand_b
    return new UUID(msb, lsb);
  }

  /** The creation instant embedded in a UUIDv7; throws if the value is not a version-7 UUID. */
  public static Instant timestampOf(UUID id) {
    if (id.version() != 7) {
      throw new IllegalArgumentException("not a UUIDv7: " + id);
    }
    return Instant.ofEpochMilli(id.getMostSignificantBits() >>> 16);
  }
}
