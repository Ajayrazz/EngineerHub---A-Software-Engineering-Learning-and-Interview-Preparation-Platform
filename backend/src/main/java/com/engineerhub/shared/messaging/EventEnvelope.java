package com.engineerhub.shared.messaging;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * The wire format of every domain event (docs/05 §6). {@code eventId} is also the AMQP message id
 * and the consumers' idempotency key.
 *
 * @param type dotted lower-case name, also the routing key, e.g. {@code lesson.completed}
 * @param version schema version of {@code payload}; consumers must tolerate older versions
 * @param correlationId id of the request/flow that caused the event (for tracing across processes)
 */
public record EventEnvelope(
    UUID eventId,
    String type,
    int version,
    Instant occurredAt,
    String aggregateType,
    String aggregateId,
    String correlationId,
    JsonNode payload) {}
