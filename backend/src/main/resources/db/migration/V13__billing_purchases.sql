-- V13: purchase intents, payments and webhook events (docs/21 §2, §3, §6) for ONE_TIME passes via Razorpay (test mode).
--  * A purchase intent snapshots the price at the moment of purchase (amount, currency, mode, period): later price
--    changes never alter what an intent or payment means. The client only ever names a plan_price_id.
--  * Access is granted ONLY by the payment-captured handler after a verified webhook, never from a browser signal.
--  * Idempotency: unique provider_order_ref per intent, unique provider_payment_ref per payment, the unique partial
--    index on access_periods(source_ref) for payments (V12), and webhook_events(provider, event_id).
--  * Webhook payloads are stored verbatim (they contain payment metadata but no card data: Razorpay Checkout keeps
--    card data away from us).

CREATE TABLE billing.purchase_intents (
    id                 uuid         NOT NULL,
    user_id            uuid         NOT NULL,
    plan_id            uuid         NOT NULL,
    plan_price_id      uuid         NOT NULL,
    billing_mode       varchar(10)  NOT NULL,
    period_unit        varchar(5)   NOT NULL,
    currency           char(3)      NOT NULL,
    amount_minor       bigint       NOT NULL,
    status             varchar(16)  NOT NULL DEFAULT 'created',
    provider           varchar(20)  NOT NULL,
    provider_order_ref varchar(60),
    idempotency_key    varchar(80),
    created_at         timestamptz  NOT NULL,
    expires_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    CONSTRAINT pk_purchase_intents PRIMARY KEY (id),
    CONSTRAINT fk_intents_plan FOREIGN KEY (plan_id) REFERENCES billing.plans (id),
    CONSTRAINT fk_intents_price FOREIGN KEY (plan_price_id) REFERENCES billing.plan_prices (id),
    CONSTRAINT ck_intents_status CHECK (status IN ('created','payment_pending','paid','failed','expired','needs_review')),
    CONSTRAINT ck_intents_amount CHECK (amount_minor BETWEEN 100 AND 100000000)
);
CREATE UNIQUE INDEX uq_intents_order ON billing.purchase_intents (provider, provider_order_ref) WHERE provider_order_ref IS NOT NULL;
CREATE UNIQUE INDEX uq_intents_idempotency ON billing.purchase_intents (user_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_intents_user ON billing.purchase_intents (user_id, created_at DESC);

CREATE TABLE billing.payments (
    id                   uuid        NOT NULL,
    intent_id            uuid        NOT NULL,
    user_id              uuid        NOT NULL,
    provider             varchar(20) NOT NULL,
    provider_payment_ref varchar(60) NOT NULL,
    amount_minor         bigint      NOT NULL,
    currency             char(3)     NOT NULL,
    status               varchar(20) NOT NULL DEFAULT 'captured',
    captured_at          timestamptz NOT NULL,
    created_at           timestamptz NOT NULL,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT fk_payments_intent FOREIGN KEY (intent_id) REFERENCES billing.purchase_intents (id),
    CONSTRAINT uq_payments_provider_ref UNIQUE (provider, provider_payment_ref),
    CONSTRAINT ck_payments_status CHECK (status IN ('captured','refunded','partially_refunded'))
);
CREATE INDEX idx_payments_user ON billing.payments (user_id, created_at DESC);

CREATE TABLE billing.webhook_events (
    provider     varchar(20)  NOT NULL,
    event_id     varchar(100) NOT NULL,
    event_type   varchar(60),
    received_at  timestamptz  NOT NULL,
    payload      text         NOT NULL,
    processed_at timestamptz,
    outcome      varchar(40),
    CONSTRAINT pk_webhook_events PRIMARY KEY (provider, event_id)
);
