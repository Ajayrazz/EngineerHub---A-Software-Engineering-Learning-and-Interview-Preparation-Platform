-- V1: shared kernel baseline (schema-per-module convention, docs/06).
-- Module schemas are created by the migration that introduces the module's first table.

CREATE SCHEMA IF NOT EXISTS shared;

-- Idempotency ledger for message consumers and webhook-style handlers (docs/04 §3, docs/21 §6).
-- A consumer records (consumer, message_id) in the same transaction as its side effects; a duplicate
-- delivery then hits the primary key and is skipped.
CREATE TABLE shared.processed_messages (
    consumer      varchar(100) NOT NULL,
    message_id    uuid         NOT NULL,
    processed_at  timestamptz  NOT NULL,
    CONSTRAINT pk_processed_messages PRIMARY KEY (consumer, message_id)
);

-- Supports the retention purge job (delete rows older than N days).
CREATE INDEX idx_processed_messages_processed_at ON shared.processed_messages (processed_at);

COMMENT ON TABLE shared.processed_messages IS
    'Consumer idempotency ledger: one row per (consumer, message) successfully handled.';
