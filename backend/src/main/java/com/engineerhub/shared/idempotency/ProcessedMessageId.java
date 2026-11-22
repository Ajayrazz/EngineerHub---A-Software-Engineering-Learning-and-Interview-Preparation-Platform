package com.engineerhub.shared.idempotency;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite primary key of {@link ProcessedMessage}. */
class ProcessedMessageId implements Serializable {

  private String consumer;
  private UUID messageId;

  protected ProcessedMessageId() {}

  ProcessedMessageId(String consumer, UUID messageId) {
    this.consumer = consumer;
    this.messageId = messageId;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof ProcessedMessageId other
        && Objects.equals(consumer, other.consumer)
        && Objects.equals(messageId, other.messageId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(consumer, messageId);
  }
}
