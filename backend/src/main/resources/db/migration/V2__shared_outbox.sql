-- V2: transactional outbox (docs/04 §3, docs/05 §6, ADR-004).
-- A module appends an event in the SAME transaction as its state change; the relay publishes it to
-- RabbitMQ afterwards. Delivery is at-least-once, so consumers must be idempotent (shared.processed_messages).

CREATE TABLE shared.outbox (
    id              uuid         NOT NULL,                 -- event id == AMQP messageId == idempotency key
    event_type      varchar(100) NOT NULL,                 -- e.g. lesson.completed (also the routing key)
    event_version   integer      NOT NULL CHECK (event_version >= 1),
    aggregate_type  varchar(100) NOT NULL,
    aggregate_id    varchar(100) NOT NULL,
    payload         jsonb        NOT NULL,
    correlation_id  varchar(64),
    occurred_at     timestamptz  NOT NULL,
    created_at      timestamptz  NOT NULL,
    published_at    timestamptz,                           -- NULL = still pending
    attempts        integer      NOT NULL DEFAULT 0,       -- failed publish attempts
    last_error      varchar(500),                          -- short, sanitized failure reason
    CONSTRAINT pk_outbox PRIMARY KEY (id)
);

-- The relay's hot query: oldest pending rows first. Partial, so it stays tiny however large the table gets.
CREATE INDEX idx_outbox_pending ON shared.outbox (created_at, id) WHERE published_at IS NULL;

-- Retention purge of already-published rows.
CREATE INDEX idx_outbox_published ON shared.outbox (published_at) WHERE published_at IS NOT NULL;

COMMENT ON TABLE shared.outbox IS 'Transactional outbox: events committed with the business change, published to RabbitMQ by the relay.';
