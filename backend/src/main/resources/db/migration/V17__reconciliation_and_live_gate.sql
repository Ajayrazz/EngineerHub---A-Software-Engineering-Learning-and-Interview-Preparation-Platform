-- V17: reconciliation against the payment provider and the Live Readiness Gate (docs/21 §6.7, §9).
--  * Reconciliation compares what the provider says with what we recorded and stores DRIFT as findings for a human:
--    it never silently changes money records. Two kinds can be applied by an admin (a payment whose webhook was lost,
--    a refund stuck pending); everything else is acknowledged with a note.
--  * At most one run is 'running' at a time (partial unique index), so a nightly job and a manual run cannot overlap.
--  * The gate holds the owner's attestations. Nothing here enables live payments: the application refuses live mode
--    at startup whatever the gate says (ADR-055). `live_ready` is the recorded precondition for ever lifting that.

CREATE TABLE billing.reconciliation_runs (
    id                uuid         NOT NULL,
    trigger           varchar(10)  NOT NULL,
    started_at        timestamptz  NOT NULL,
    finished_at       timestamptz,
    window_from       timestamptz  NOT NULL,
    window_to         timestamptz  NOT NULL,
    status            varchar(10)  NOT NULL DEFAULT 'running',
    provider_payments integer      NOT NULL DEFAULT 0,
    local_payments    integer      NOT NULL DEFAULT 0,
    findings_new      integer      NOT NULL DEFAULT 0,
    intents_expired   integer      NOT NULL DEFAULT 0,
    note              varchar(500),
    CONSTRAINT pk_reconciliation_runs PRIMARY KEY (id),
    CONSTRAINT ck_reconciliation_trigger CHECK (trigger IN ('scheduled', 'manual')),
    CONSTRAINT ck_reconciliation_status CHECK (status IN ('running', 'ok', 'drift', 'partial', 'error', 'skipped'))
);
CREATE UNIQUE INDEX uq_reconciliation_one_running ON billing.reconciliation_runs ((true)) WHERE status = 'running';
CREATE INDEX idx_reconciliation_runs_started ON billing.reconciliation_runs (started_at DESC);

CREATE TABLE billing.reconciliation_findings (
    id               uuid         NOT NULL,
    run_id           uuid         NOT NULL,
    kind             varchar(30)  NOT NULL,
    status           varchar(10)  NOT NULL DEFAULT 'open',
    provider_ref     varchar(60)  NOT NULL,
    order_ref        varchar(60),
    local_payment_id uuid,
    local_refund_id  uuid,
    amount_minor     bigint,
    currency         char(3),
    detail           varchar(500) NOT NULL,
    created_at       timestamptz  NOT NULL,
    resolved_at      timestamptz,
    resolved_by      uuid,
    resolution       varchar(12),
    resolution_note  varchar(500),
    CONSTRAINT pk_reconciliation_findings PRIMARY KEY (id),
    CONSTRAINT fk_findings_run FOREIGN KEY (run_id) REFERENCES billing.reconciliation_runs (id),
    CONSTRAINT ck_findings_kind CHECK (kind IN ('missing_payment', 'unknown_provider_payment', 'amount_mismatch',
        'refund_amount_mismatch', 'refund_stuck', 'missing_at_provider')),
    CONSTRAINT ck_findings_status CHECK (status IN ('open', 'resolved')),
    CONSTRAINT ck_findings_resolution CHECK (resolution IS NULL OR resolution IN ('applied', 'acknowledged'))
);
-- a drift that is still open is reported once, however many runs see it
CREATE UNIQUE INDEX uq_findings_open ON billing.reconciliation_findings (kind, provider_ref) WHERE status = 'open';
CREATE INDEX idx_findings_status ON billing.reconciliation_findings (status, created_at DESC);

CREATE TABLE billing.live_gate (
    id         smallint    NOT NULL,
    live_ready boolean     NOT NULL DEFAULT false,
    changed_at timestamptz NOT NULL,
    changed_by uuid,
    CONSTRAINT pk_live_gate PRIMARY KEY (id),
    CONSTRAINT ck_live_gate_single CHECK (id = 1)
);
INSERT INTO billing.live_gate (id, live_ready, changed_at) VALUES (1, false, now());

CREATE TABLE billing.live_readiness (
    item_code    varchar(40)  NOT NULL,
    done         boolean      NOT NULL DEFAULT false,
    note         varchar(500),
    confirmed_by uuid,
    confirmed_at timestamptz,
    CONSTRAINT pk_live_readiness PRIMARY KEY (item_code),
    CONSTRAINT ck_live_readiness_item CHECK (item_code IN ('legal_entity', 'razorpay_kyc', 'bank_details',
        'tax_mode_confirmed', 'policies_published', 'live_keys_installed', 'webhook_verified_live',
        'seller_details_real', 'test_checklist_signed'))
);
INSERT INTO billing.live_readiness (item_code) VALUES
    ('legal_entity'), ('razorpay_kyc'), ('bank_details'), ('tax_mode_confirmed'), ('policies_published'),
    ('live_keys_installed'), ('webhook_verified_live'), ('seller_details_real'), ('test_checklist_signed');
