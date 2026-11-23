-- V15: tax configuration and receipts / tax invoices (docs/21 §5).
--  * NOTHING tax-related is assumed (Pending Decision P-4): the single settings row starts UNCONFIGURED, no tax rule
--    exists, and no document is issued until the owner chooses DISABLED (receipts, no tax wording at all) or ENABLED
--    (tax invoices from rules the owner enters).
--  * Documents are immutable snapshots (price, tax rule and rate, seller details at issue time): later configuration
--    edits never change an issued document. A trigger refuses UPDATE and DELETE.
--  * Numbers are gap-free per financial year and document type: allocated from a row-locked counter in the SAME
--    transaction that inserts the document, so a failed issue leaves no hole.
--  * One document per payment (unique). Credit notes arrive with refunds (M6.6): no trigger exists for them yet.

CREATE TABLE billing.tax_settings (
    id                smallint     NOT NULL,
    tax_mode          varchar(14)  NOT NULL DEFAULT 'UNCONFIGURED',
    seller_legal_name varchar(200),
    seller_address    varchar(500),
    seller_state      varchar(60),
    seller_gstin      varchar(15),
    sac_code          varchar(8),
    updated_at        timestamptz  NOT NULL,
    updated_by        uuid,
    CONSTRAINT pk_tax_settings PRIMARY KEY (id),
    CONSTRAINT ck_tax_settings_single CHECK (id = 1),
    CONSTRAINT ck_tax_settings_mode CHECK (tax_mode IN ('UNCONFIGURED', 'DISABLED', 'ENABLED'))
);
-- the configuration row itself (not a business value): the mode is UNCONFIGURED until the owner decides
INSERT INTO billing.tax_settings (id, tax_mode, updated_at) VALUES (1, 'UNCONFIGURED', now());

CREATE TABLE billing.tax_rules (
    id         uuid         NOT NULL,
    name       varchar(60)  NOT NULL,
    rate_bps   integer      NOT NULL,
    valid_from date         NOT NULL,
    valid_to   date,
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL,
    CONSTRAINT pk_tax_rules PRIMARY KEY (id),
    CONSTRAINT ck_tax_rules_rate CHECK (rate_bps BETWEEN 1 AND 5000),
    CONSTRAINT ck_tax_rules_range CHECK (valid_to IS NULL OR valid_to > valid_from)
);

CREATE TABLE billing.document_sequences (
    financial_year varchar(7)  NOT NULL,
    doc_type       varchar(12) NOT NULL,
    last_no        bigint      NOT NULL,
    CONSTRAINT pk_document_sequences PRIMARY KEY (financial_year, doc_type)
);

CREATE TABLE billing.documents (
    id                uuid         NOT NULL,
    payment_id        uuid         NOT NULL,
    user_id           uuid         NOT NULL,
    doc_type          varchar(12)  NOT NULL,
    doc_number        varchar(40)  NOT NULL,
    financial_year    varchar(7)   NOT NULL,
    issued_at         timestamptz  NOT NULL,
    paid_at           timestamptz  NOT NULL,
    currency          char(3)      NOT NULL,
    total_minor       bigint       NOT NULL,
    net_minor         bigint       NOT NULL,
    tax_minor         bigint       NOT NULL,
    tax_rate_bps      integer,
    tax_rule_name     varchar(60),
    plan_name         varchar(100) NOT NULL,
    period_unit       varchar(5)   NOT NULL,
    billing_mode      varchar(10)  NOT NULL,
    customer_name     varchar(200) NOT NULL,
    seller_legal_name varchar(200),
    seller_address    varchar(500),
    seller_state      varchar(60),
    seller_gstin      varchar(15),
    sac_code          varchar(8),
    test_mode         boolean      NOT NULL,
    CONSTRAINT pk_documents PRIMARY KEY (id),
    CONSTRAINT fk_documents_payment FOREIGN KEY (payment_id) REFERENCES billing.payments (id),
    CONSTRAINT uq_documents_payment UNIQUE (payment_id),
    CONSTRAINT uq_documents_number UNIQUE (doc_number),
    CONSTRAINT ck_documents_type CHECK (doc_type IN ('receipt', 'tax_invoice')),
    CONSTRAINT ck_documents_sum CHECK (net_minor + tax_minor = total_minor),
    CONSTRAINT ck_documents_tax_only_on_invoice CHECK (doc_type = 'tax_invoice' OR tax_minor = 0)
);
CREATE INDEX idx_documents_user ON billing.documents (user_id, issued_at DESC);

CREATE FUNCTION billing.documents_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'billing.documents rows are immutable';
END;
$$;
CREATE TRIGGER trg_documents_immutable BEFORE UPDATE OR DELETE ON billing.documents
    FOR EACH ROW EXECUTE FUNCTION billing.documents_immutable();
