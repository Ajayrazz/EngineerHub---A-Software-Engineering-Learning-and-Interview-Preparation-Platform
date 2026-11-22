-- V3: identity module — local user records keyed by the Keycloak subject (docs/06 §identity).
-- Passwords and MFA live in Keycloak only. This table holds the application's view of a user.

CREATE SCHEMA IF NOT EXISTS identity;

CREATE TABLE identity.users (
    id             uuid         NOT NULL,
    keycloak_sub   varchar(64)  NOT NULL,                  -- the IdP's stable subject id
    username       varchar(64)  NOT NULL,
    email          varchar(254),                           -- PII
    email_verified boolean      NOT NULL DEFAULT false,
    status         varchar(20)  NOT NULL DEFAULT 'active',
    timezone       varchar(64)  NOT NULL DEFAULT 'UTC',    -- IANA id; drives streak/day boundaries
    locale         varchar(16)  NOT NULL DEFAULT 'en',
    created_at     timestamptz  NOT NULL,
    updated_at     timestamptz  NOT NULL,
    last_login_at  timestamptz  NOT NULL,
    version        bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_keycloak_sub UNIQUE (keycloak_sub),
    CONSTRAINT ck_users_status CHECK (status IN ('active', 'suspended', 'pending_deletion', 'deleted'))
);

CREATE UNIQUE INDEX uq_users_username_lower ON identity.users (lower(username));
CREATE INDEX idx_users_email_lower ON identity.users (lower(email));
CREATE INDEX idx_users_created_at ON identity.users (created_at, id);

CREATE TABLE identity.user_profiles (
    user_id       uuid         NOT NULL,
    display_name  varchar(100),
    avatar_key    varchar(255),
    bio           varchar(500),
    website       varchar(300),
    github_url    varchar(300),
    linkedin_url  varchar(300),
    updated_at    timestamptz  NOT NULL,
    version       bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_profiles PRIMARY KEY (user_id),
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES identity.users (id) ON DELETE CASCADE
);

COMMENT ON TABLE identity.users IS 'Local user record, provisioned just-in-time from the first authenticated request.';
