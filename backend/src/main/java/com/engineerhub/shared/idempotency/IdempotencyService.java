package com.engineerhub.shared.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exactly-once <em>effect</em> on top of at-least-once delivery. Call {@link #firstDelivery} inside
 * the same transaction as the handler's side effects: if the transaction rolls back, the ledger row
 * rolls back too and the retry is processed normally.
 */
@Service
public class IdempotencyService {

  private final ProcessedMessageRepository repository;
  private final Clock clock;

  IdempotencyService(ProcessedMessageRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  /** {@code true} the first time this (consumer, message) is seen; {@code false} for duplicates. */
  @Transactional
  public boolean firstDelivery(String consumer, UUID messageId) {
    return repository.insertIfAbsent(consumer, messageId, clock.instant()) == 1;
  }

  /** Retention purge: forget ledger rows older than {@code retention}. Returns rows removed. */
  @Transactional
  public int purgeOlderThan(Duration retention) {
    return repository.deleteOlderThan(clock.instant().minus(retention));
  }
}
