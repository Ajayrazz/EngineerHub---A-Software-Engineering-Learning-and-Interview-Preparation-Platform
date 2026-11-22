package com.engineerhub.shared.idempotency;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, ProcessedMessageId> {

  /**
   * Atomically records a message as processed. Returns 1 when this call inserted the row (first
   * delivery) and 0 when it already existed (duplicate). Race-free: decided by the primary key, not
   * by a read-then-write.
   */
  @Modifying
  @Query(
      nativeQuery = true,
      value =
          "insert into shared.processed_messages (consumer, message_id, processed_at) "
              + "values (:consumer, :messageId, :at) on conflict (consumer, message_id) do nothing")
  int insertIfAbsent(
      @Param("consumer") String consumer,
      @Param("messageId") UUID messageId,
      @Param("at") Instant at);

  @Modifying
  @Query("delete from ProcessedMessage m where m.processedAt < :cutoff")
  int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
