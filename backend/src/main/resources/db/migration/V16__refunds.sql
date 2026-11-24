-- V16: refunds (docs/21 §7): requests, admin review, provider refunds, credit notes.
--  * NO refund policy text is shipped (Pending Decision P-6): the single settings row starts with no policy, no
--    request window and no approval threshold; the owner writes them. revoke_access_on_refund defaults to true as
--    docs/21 proposes. A request is always possible; the owner decides every outcome.
--  * One live request per payment (partial unique index). A refund is a separate record, so a failed provider call
--    can be retried without losing history; the sum of pending and processed refunds never exceeds the payment.
--  * Credit notes are immutable snapshots like documents (trigger), gap-free numbered, one per refund, and exist
--    only when the payment had a document.

CREATE TABLE billing.refund_settings (
    id                       smallint     NOT NULL,
    policy_text              varchar(10000),
    request_window_days      integer,
    revoke_access_on_refund  boolean      NOT NULL DEFAULT true,
    approval_threshold_minor bigint,
    auto_detect_duplicates   boolean      NOT NULL DEFAULT true,
    updated_at               timestamptz  NOT NULL,
    updated_by               uuid,
    CONSTRAINT pk_refund_settings PRIMARY KEY (id),
    CONSTRAINT ck_refund_settings_single CHECK (id = 1),
    CONSTRAINT ck_refund_settings_window CHECK (request_window_days IS NULL OR request_window_days BETWEEN 1 AND 3660),
    CONSTRAINT ck_refund_settings_threshold CHECK (approval_threshold_minor IS NULL OR approval_threshold_minor >= 100)
);
INSERT INTO billing.refund_settings (id, updated_at) VALUES (1, now());

CREATE TABLE billing.refund_requests (
    id                    uuid          NOT NULL,
    payment_id            uuid          NOT NULL,
    user_id               uuid          NOT NULL,
    reason                varchar(30)   NOT NULL,
    details               varchar(2000),
    status                varchar(16)   NOT NULL DEFAULT 'requested',
    reviewer_id           uuid,
    decided_by            uuid,
    decision_note         varchar(1000),
    approved_amount_minor bigint,
    created_at            timestamptz   NOT NULL,
    updated_at            timestamptz   NOT NULL,
    CONSTRAINT pk_refund_requests PRIMARY KEY (id),
    CONSTRAINT fk_refund_requests_payment FOREIGN KEY (payment_id) REFERENCES billing.payments (id),
    CONSTRAINT ck_refund_requests_reason CHECK (reason IN ('duplicate_payment', 'not_activated', 'technical_issue', 'other')),
    CONSTRAINT ck_refund_requests_status CHECK (status IN ('requested','under_review','approved','processing','processed','rejected','failed')),
    CONSTRAINT ck_refund_requests_amount CHECK (approved_amount_minor IS NULL OR approved_amount_minor > 0)
);
CREATE UNIQUE INDEX uq_refund_requests_live ON billing.refund_requests (payment_id)
    WHERE status IN ('requested','under_review','approved','processing','failed');
CREATE INDEX idx_refund_requests_user ON billing.refund_requests (user_id, created_at DESC);
CREATE INDEX idx_refund_requests_status ON billing.refund_requests (status, created_at);

CREATE TABLE billing.refunds (
    id                 uuid         NOT NULL,
    request_id         uuid         NOT NULL,
    payment_id         uuid         NOT NULL,
    amount_minor       bigint       NOT NULL,
    provider           varchar(20)  NOT NULL,
    provider_refund_ref varchar(60),
    status             varchar(12)  NOT NULL DEFAULT 'pending',
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    CONSTRAINT pk_refunds PRIMARY KEY (id),
    CONSTRAINT fk_refunds_request FOREIGN KEY (request_id) REFERENCES billing.refund_requests (id),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES billing.payments (id),
    CONSTRAINT ck_refunds_status CHECK (status IN ('pending','processed','failed')),
    CONSTRAINT ck_refunds_amount CHECK (amount_minor > 0)
);
CREATE UNIQUE INDEX uq_refunds_ref ON billing.refunds (provider, provider_refund_ref) WHERE provider_refund_ref IS NOT NULL;
CREATE INDEX idx_refunds_payment ON billing.refunds (payment_id);

CREATE TABLE billing.credit_notes (
    id                  uuid         NOT NULL,
    refund_id           uuid         NOT NULL,
    document_id         uuid         NOT NULL,
    user_id             uuid         NOT NULL,
    doc_number          varchar(40)  NOT NULL,
    original_number     varchar(40)  NOT NULL,
    financial_year      varchar(7)   NOT NULL,
    issued_at           timestamptz  NOT NULL,
    currency            char(3)      NOT NULL,
    total_minor         bigint       NOT NULL,
    net_minor           bigint       NOT NULL,
    tax_minor           bigint       NOT NULL,
    tax_rate_bps        integer,
    tax_rule_name       varchar(60),
    plan_name           varchar(100) NOT NULL,
    period_unit         varchar(5)   NOT NULL,
    billing_mode        varchar(10)  NOT NULL,
    customer_name       varchar(200) NOT NULL,
    seller_legal_name   varchar(200),
    seller_address      varchar(500),
    seller_state        varchar(60),
    seller_gstin        varchar(15),
    sac_code            varchar(8),
    test_mode           boolean      NOT NULL,
    CONSTRAINT pk_credit_notes PRIMARY KEY (id),
    CONSTRAINT fk_credit_notes_refund FOREIGN KEY (refund_id) REFERENCES billing.refunds (id),
    CONSTRAINT fk_credit_notes_document FOREIGN KEY (document_id) REFERENCES billing.documents (id),
    CONSTRAINT uq_credit_notes_refund UNIQUE (refund_id),
    CONSTRAINT uq_credit_notes_number UNIQUE (doc_number),
    CONSTRAINT ck_credit_notes_sum CHECK (net_minor + tax_minor = total_minor)
);
CREATE INDEX idx_credit_notes_user ON billing.credit_notes (user_id, issued_at DESC);

CREATE TRIGGER trg_credit_notes_immutable BEFORE UPDATE OR DELETE ON billing.credit_notes
    FOR EACH ROW EXECUTE FUNCTION billing.documents_immutable();
