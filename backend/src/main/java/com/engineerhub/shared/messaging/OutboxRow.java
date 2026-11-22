package com.engineerhub.shared.messaging;

import java.time.Instant;
import java.util.UUID;

/** One row of {@code shared.outbox}. */
record OutboxRow(
    UUID id,
    String eventType,
    int eventVersion,
    String aggregateType,
    String aggregateId,
    String payloadJson,
    String correlationId,
    Instant occurredAt,
    Instant createdAt) {}
