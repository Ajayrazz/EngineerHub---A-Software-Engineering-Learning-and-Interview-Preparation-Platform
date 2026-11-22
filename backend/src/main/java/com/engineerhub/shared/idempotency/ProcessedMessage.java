package com.engineerhub.shared.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Row of the consumer idempotency ledger (table {@code shared.processed_messages}). */
@Entity
@Table(schema = "shared", name = "processed_messages")
@IdClass(ProcessedMessageId.class)
class ProcessedMessage {

  @Id
  @Column(name = "consumer", length = 100, nullable = false, updatable = false)
  private String consumer;

  @Id
  @Column(name = "message_id", nullable = false, updatable = false)
  private UUID messageId;

  @Column(name = "processed_at", nullable = false, updatable = false)
  private Instant processedAt;

  protected ProcessedMessage() {}
}
