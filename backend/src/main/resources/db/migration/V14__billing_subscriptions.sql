-- V14: auto-renewing subscriptions (docs/21 §2, §3, §6) via Razorpay Subscriptions (test mode).
--  * A subscription snapshots the price (amount, currency, period) like a purchase intent does. The provider "plan"
--    of a RECURRING price is created lazily once and cached on the price (a provider plan's amount is fixed, and
--    prices are never edited, only switched off, so the mapping is 1:1).
--  * Access is granted ONLY by the subscription-charged handler after a verified webhook, exactly like passes:
--    every charge becomes an immutable payment + access period through the same code path.
--  * Status never regresses on late or duplicated events (guarded updates). There is NO grace period yet (Pending
--    Decision P-2): access ends at the paid-through date of the last successful charge.
--  * At most ONE live subscription per user and plan (Pending Decision P-1 proposed default), enforced by the
--    database, not only by code.

ALTER TABLE billing.plan_prices ADD COLUMN provider_plan_ref varchar(60);

CREATE TABLE billing.subscriptions (
    id                        uuid         NOT NULL,
    user_id                   uuid         NOT NULL,
    plan_id                   uuid         NOT NULL,
    plan_price_id             uuid         NOT NULL,
    period_unit               varchar(5)   NOT NULL,
    currency                  char(3)      NOT NULL,
    amount_minor              bigint       NOT NULL,
    status                    varchar(20)  NOT NULL DEFAULT 'created',
    provider                  varchar(20)  NOT NULL,
    provider_subscription_ref varchar(60),
    idempotency_key           varchar(80),
    cancel_requested_at       timestamptz,
    created_at                timestamptz  NOT NULL,
    updated_at                timestamptz  NOT NULL,
    CONSTRAINT pk_subscriptions PRIMARY KEY (id),
    CONSTRAINT fk_subscriptions_plan FOREIGN KEY (plan_id) REFERENCES billing.plans (id),
    CONSTRAINT fk_subscriptions_price FOREIGN KEY (plan_price_id) REFERENCES billing.plan_prices (id),
    CONSTRAINT ck_subscriptions_status CHECK (status IN
        ('created','pending','active','past_due','cancel_scheduled','cancelled','completed','failed','needs_review')),
    CONSTRAINT ck_subscriptions_amount CHECK (amount_minor BETWEEN 100 AND 100000000)
);
CREATE UNIQUE INDEX uq_subscriptions_ref ON billing.subscriptions (provider, provider_subscription_ref)
    WHERE provider_subscription_ref IS NOT NULL;
CREATE UNIQUE INDEX uq_subscriptions_idempotency ON billing.subscriptions (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
-- one live subscription per user and plan
CREATE UNIQUE INDEX uq_subscriptions_live ON billing.subscriptions (user_id, plan_id)
    WHERE status IN ('created','pending','active','past_due','cancel_scheduled');
CREATE INDEX idx_subscriptions_user ON billing.subscriptions (user_id, created_at DESC);

-- a payment belongs to exactly one of: a one-time purchase intent, a subscription
ALTER TABLE billing.payments ALTER COLUMN intent_id DROP NOT NULL;
ALTER TABLE billing.payments ADD COLUMN subscription_id uuid;
ALTER TABLE billing.payments ADD CONSTRAINT fk_payments_subscription
    FOREIGN KEY (subscription_id) REFERENCES billing.subscriptions (id);
ALTER TABLE billing.payments ADD CONSTRAINT ck_payments_owner
    CHECK ((intent_id IS NULL) <> (subscription_id IS NULL));
CREATE INDEX idx_payments_subscription ON billing.payments (subscription_id) WHERE subscription_id IS NOT NULL;
