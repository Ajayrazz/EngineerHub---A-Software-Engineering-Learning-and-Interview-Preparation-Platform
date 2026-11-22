package com.engineerhub.shared.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdsTest {

  @Test
  void producesRfc9562Version7Variant2() {
    UUID id = Ids.newId();
    assertThat(id.version()).isEqualTo(7);
    assertThat(id.variant()).isEqualTo(2);
  }

  @Test
  void embedsAndRecoversTheTimestamp() {
    long millis = 1_800_000_000_123L;
    assertThat(Ids.timestampOf(Ids.newId(millis))).isEqualTo(Instant.ofEpochMilli(millis));
  }

  @Test
  void currentIdsCarryTheCurrentTime() {
    long before = System.currentTimeMillis();
    Instant ts = Ids.timestampOf(Ids.newId());
    long after = System.currentTimeMillis();
    assertThat(ts.toEpochMilli()).isBetween(before, after);
  }

  @Test
  void idsSortByCreationTimeWhenRenderedAsStrings() {
    // Lexicographic order of the canonical text == chronological order (what makes UUIDv7
    // index-friendly)
    String earlier = Ids.newId(1_800_000_000_000L).toString();
    String later = Ids.newId(1_800_000_000_001L).toString();
    assertThat(earlier).isLessThan(later);
  }

  @Test
  void idsAreUniqueEvenWithinTheSameMillisecond() {
    Set<UUID> seen = new HashSet<>();
    for (int i = 0; i < 200_000; i++) {
      assertThat(seen.add(Ids.newId(1_800_000_000_000L))).isTrue();
    }
  }

  @Test
  void rejectsOutOfRangeTimestamps() {
    assertThatThrownBy(() -> Ids.newId(-1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Ids.newId(1L << 48)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void timestampOfRejectsNonV7Ids() {
    assertThatThrownBy(() -> Ids.timestampOf(UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
