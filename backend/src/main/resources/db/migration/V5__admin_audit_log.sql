-- V5: append-only, hash-chained audit log for privileged actions (docs/06 §admin, docs/09 T22).
--  * written in the SAME transaction as the audited change (an entry exists iff the action committed)
--  * UPDATE / DELETE / TRUNCATE are rejected by triggers (append-only)
--  * every row stores hash = SHA-256(prev_hash || canonical fields): editing or removing history breaks the
--    chain, which AuditVerifier detects. (The application DB user owns the table, so a database
--    superuser could still drop the triggers: a separate low-privilege runtime role is a hosting-phase TODO.)

CREATE SCHEMA IF NOT EXISTS admin;

CREATE TABLE admin.audit_logs (
    id              bigint GENERATED ALWAYS AS IDENTITY,
    occurred_at     timestamptz  NOT NULL,
    actor_id        uuid,                          -- local user id (NULL for system actors)
    actor_sub       varchar(64)  NOT NULL,         -- Keycloak subject, stable even if the user row is later purged
    actor_roles     varchar(200) NOT NULL,
    action          varchar(100) NOT NULL,         -- e.g. user.status.changed
    resource_type   varchar(40)  NOT NULL,
    resource_id     varchar(100) NOT NULL,
    details_before  text,                          -- canonical JSON (sorted keys) — kept as text so the hash is reproducible
    details_after   text,
    reason          varchar(500),
    remote_addr     varchar(64),
    forwarded_for   varchar(200),
    user_agent      varchar(300),
    correlation_id  varchar(64),
    prev_hash       bytea        NOT NULL,
    hash            bytea        NOT NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id)
);

CREATE INDEX idx_audit_logs_occurred ON admin.audit_logs (occurred_at DESC, id DESC);
CREATE INDEX idx_audit_logs_actor ON admin.audit_logs (actor_sub, id DESC);
CREATE INDEX idx_audit_logs_resource ON admin.audit_logs (resource_type, resource_id, id DESC);
CREATE INDEX idx_audit_logs_action ON admin.audit_logs (action, id DESC);

CREATE FUNCTION admin.audit_logs_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'admin.audit_logs is append-only (% rejected)', TG_OP USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_logs_no_update_delete BEFORE UPDATE OR DELETE ON admin.audit_logs
    FOR EACH ROW EXECUTE FUNCTION admin.audit_logs_immutable();
CREATE TRIGGER audit_logs_no_truncate BEFORE TRUNCATE ON admin.audit_logs
    FOR EACH STATEMENT EXECUTE FUNCTION admin.audit_logs_immutable();
