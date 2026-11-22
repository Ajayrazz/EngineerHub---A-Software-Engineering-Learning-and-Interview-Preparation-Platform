package com.engineerhub.shared.messaging;

import com.engineerhub.shared.id.Ids;
import com.engineerhub.shared.web.CorrelationId;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * The only way a module emits a domain event. The event is appended to the outbox <b>in the
 * caller's transaction</b>: it exists if and only if the business change commits. A relay publishes
 * it to RabbitMQ afterwards (at-least-once), so consumers must be idempotent.
 *
 * <p>Calling this outside a transaction is a programming error and fails immediately ({@code
 * MANDATORY}) — otherwise the "event without state change / state change without event" problem the
 * outbox exists to prevent would come back.
 */
@Service
public class OutboxPublisher {

  private final OutboxStore store;
  private final ObjectMapper mapper;
  private final Clock clock;

  OutboxPublisher(OutboxStore store, ObjectMapper mapper, Clock clock) {
    this.store = store;
    this.mapper = mapper;
    this.clock = clock;
  }

  /**
   * @param eventType dotted lower-case name (also the routing key), e.g. {@code lesson.completed}
   * @param version schema version of the payload (start at 1)
   * @param aggregateType e.g. {@code lesson}
   * @param aggregateId id of the entity the event is about
   * @param payload serialisable facts; keep it small and free of secrets/PII where possible
   * @return the event id (also the consumers' idempotency key)
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public UUID publish(
      String eventType, int version, String aggregateType, String aggregateId, Object payload) {
    if (eventType == null || !EventCodec.EVENT_TYPE.matcher(eventType).matches()) {
      throw new IllegalArgumentException("invalid event type: " + eventType);
    }
    if (version < 1) {
      throw new IllegalArgumentException("event version must be >= 1");
    }
    UUID id = Ids.newId();
    Instant now = clock.instant();
    store.insert(
        new OutboxRow(
            id,
            eventType,
            version,
            aggregateType,
            aggregateId,
            mapper.writeValueAsString(payload),
            CorrelationId.current(),
            now,
            now));
    return id;
  }
}
