-- V12: billing core (docs/21 §2, §5, §8): plans, tax-inclusive prices, entitlements, access periods.
--  * NO plans or prices are seeded: names and INR prices are the owner's decision (Pending Decision P-7).
--  * Prices are TAX-INCLUSIVE final payable amounts in minor units (paise); no tax rate or wording exists yet
--    (tax mode is unconfigured, P-4).
--  * Entitlements are derived ONLY from access periods: a user is entitled to a code while a non-revoked period
--    of a plan that carries the code is running. Periods are immutable except for revocation. Today the only
--    source is an audited admin grant; payments (M6.2) will add periods with source_type 'payment'.
--  * No cross-module foreign keys: user_id points into identity.users.

CREATE SCHEMA IF NOT EXISTS billing;

CREATE TABLE billing.plans (
    id         uuid         NOT NULL,
    code       varchar(40)  NOT NULL,
    name       varchar(100) NOT NULL,
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL,
    CONSTRAINT pk_plans PRIMARY KEY (id),
    CONSTRAINT uq_plans_code UNIQUE (code)
);

CREATE TABLE billing.plan_prices (
    id                     uuid        NOT NULL,
    plan_id                uuid        NOT NULL,
    billing_mode           varchar(10) NOT NULL,
    period_unit            varchar(5)  NOT NULL,
    currency               char(3)     NOT NULL DEFAULT 'INR',
    amount_incl_tax_minor  bigint      NOT NULL,
    active                 boolean     NOT NULL DEFAULT true,
    created_at             timestamptz NOT NULL,
    CONSTRAINT pk_plan_prices PRIMARY KEY (id),
    CONSTRAINT fk_plan_prices_plan FOREIGN KEY (plan_id) REFERENCES billing.plans (id),
    CONSTRAINT ck_plan_prices_mode CHECK (billing_mode IN ('ONE_TIME','RECURRING')),
    CONSTRAINT ck_plan_prices_unit CHECK (period_unit IN ('month','year')),
    CONSTRAINT ck_plan_prices_currency CHECK (currency = 'INR'),
    CONSTRAINT ck_plan_prices_amount CHECK (amount_incl_tax_minor BETWEEN 100 AND 100000000)
);
CREATE INDEX idx_plan_prices_plan ON billing.plan_prices (plan_id, active);

CREATE TABLE billing.plan_entitlements (
    plan_id          uuid        NOT NULL,
    entitlement_code varchar(40) NOT NULL,
    CONSTRAINT pk_plan_entitlements PRIMARY KEY (plan_id, entitlement_code),
    CONSTRAINT fk_plan_entitlements_plan FOREIGN KEY (plan_id) REFERENCES billing.plans (id) ON DELETE CASCADE,
    CONSTRAINT ck_entitlement_code CHECK (entitlement_code IN ('premium_content'))
);

CREATE TABLE billing.access_periods (
    id             uuid         NOT NULL,
    user_id        uuid         NOT NULL,
    plan_id        uuid         NOT NULL,
    starts_at      timestamptz  NOT NULL,
    ends_at        timestamptz  NOT NULL,
    source_type    varchar(15)  NOT NULL,
    source_ref     varchar(100),
    granted_by     uuid,
    reason         varchar(500),
    revoked_at     timestamptz,
    revoked_by     uuid,
    revoked_reason varchar(500),
    created_at     timestamptz  NOT NULL,
    CONSTRAINT pk_access_periods PRIMARY KEY (id),
    CONSTRAINT fk_access_periods_plan FOREIGN KEY (plan_id) REFERENCES billing.plans (id),
    CONSTRAINT ck_access_periods_source CHECK (source_type IN ('admin_grant','payment')),
    CONSTRAINT ck_access_periods_range CHECK (ends_at > starts_at),
    CONSTRAINT ck_access_periods_revoked CHECK ((revoked_at IS NULL) = (revoked_by IS NULL))
);
-- one period per payment (idempotent grants from M6.2 on)
CREATE UNIQUE INDEX uq_access_periods_payment ON billing.access_periods (source_ref) WHERE source_type = 'payment';
-- "is this user entitled right now?" and "my access"
CREATE INDEX idx_access_periods_user ON billing.access_periods (user_id, ends_at DESC) WHERE revoked_at IS NULL;
