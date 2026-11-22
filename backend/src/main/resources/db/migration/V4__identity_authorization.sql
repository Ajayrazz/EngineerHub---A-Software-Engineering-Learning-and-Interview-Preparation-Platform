-- V4: authorization model (docs/08 §3). Three independent axes, all evaluated server-side:
--   1. role -> permission   (Keycloak realm roles are the *staff roles*; what they may do lives here)
--   2. scope                (scoped_grants narrow a scoped permission to specific resources)
--   3. entitlements         (paid access; separate module, not roles — ADR-006)

CREATE TABLE identity.permissions (
    code        varchar(64)  NOT NULL,
    description varchar(200) NOT NULL,
    grantable   boolean      NOT NULL DEFAULT false,   -- may be handed to a single user for specific resources
    CONSTRAINT pk_permissions PRIMARY KEY (code)
);

CREATE TABLE identity.role_permissions (
    role            varchar(40) NOT NULL,              -- Keycloak realm role name
    permission_code varchar(64) NOT NULL,
    scoped          boolean     NOT NULL DEFAULT false, -- true: only on resources the user was granted (scoped_grants)
    CONSTRAINT pk_role_permissions PRIMARY KEY (role, permission_code),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_code) REFERENCES identity.permissions (code),
    CONSTRAINT ck_role_permissions_role CHECK (role IN
        ('SUPER_ADMIN','ADMIN','CONTENT_MANAGER','PROBLEM_SETTER','MODERATOR','INSTRUCTOR'))
);

CREATE TABLE identity.scoped_grants (
    id              uuid         NOT NULL,
    user_id         uuid         NOT NULL,
    permission_code varchar(64)  NOT NULL,
    resource_type   varchar(40)  NOT NULL,
    resource_id     varchar(100) NOT NULL,
    granted_by      uuid         NOT NULL,
    granted_at      timestamptz  NOT NULL,
    expires_at      timestamptz,
    CONSTRAINT pk_scoped_grants PRIMARY KEY (id),
    CONSTRAINT fk_scoped_grants_user FOREIGN KEY (user_id) REFERENCES identity.users (id) ON DELETE CASCADE,
    CONSTRAINT fk_scoped_grants_granter FOREIGN KEY (granted_by) REFERENCES identity.users (id),
    CONSTRAINT fk_scoped_grants_permission FOREIGN KEY (permission_code) REFERENCES identity.permissions (code),
    CONSTRAINT uq_scoped_grants UNIQUE (user_id, permission_code, resource_type, resource_id)
);
CREATE INDEX idx_scoped_grants_lookup ON identity.scoped_grants (user_id, permission_code, resource_type, resource_id);

INSERT INTO identity.permissions (code, description, grantable) VALUES
  ('content.premium.read',   'Read premium content without a subscription', true),
  ('course.edit',            'Edit course content',                         true),
  ('course.publish',         'Publish or unpublish a course',               false),
  ('problem.edit',           'Create and edit problems and test cases',     true),
  ('problem.publish',        'Publish a problem',                           false),
  ('moderation.manage',      'Moderate comments, posts and reports',        false),
  ('user.read',              'View users and their accounts',               false),
  ('user.mute',              'Mute a user in the community',                false),
  ('user.suspend',           'Suspend or reactivate an account',            false),
  ('role.grant.standard',    'Grant scoped permissions (non-admin)',        false),
  ('role.grant.all',         'Grant any role or permission',                false),
  ('billing.manage',         'Manage plans, payments and refunds',          false),
  ('audit.read',             'View the audit log',                          false),
  ('settings.manage.partial','Change non-critical platform settings',       false),
  ('settings.manage',        'Change all platform settings and flags',      false);

-- Seed of the authorization matrix in docs/08 §3 (kept in sync by AuthorizationMatrixIT).
INSERT INTO identity.role_permissions (role, permission_code, scoped)
SELECT 'SUPER_ADMIN', code, false FROM identity.permissions;

INSERT INTO identity.role_permissions (role, permission_code, scoped) VALUES
  ('ADMIN','content.premium.read',false), ('ADMIN','course.edit',false), ('ADMIN','course.publish',false),
  ('ADMIN','problem.edit',false), ('ADMIN','problem.publish',false), ('ADMIN','moderation.manage',false),
  ('ADMIN','user.read',false), ('ADMIN','user.suspend',false), ('ADMIN','role.grant.standard',false),
  ('ADMIN','billing.manage',false), ('ADMIN','audit.read',false), ('ADMIN','settings.manage.partial',false),
  ('CONTENT_MANAGER','content.premium.read',false), ('CONTENT_MANAGER','course.edit',false), ('CONTENT_MANAGER','course.publish',false),
  ('MODERATOR','moderation.manage',false), ('MODERATOR','user.mute',false),
  ('INSTRUCTOR','course.edit',true), ('INSTRUCTOR','content.premium.read',true),
  ('PROBLEM_SETTER','problem.edit',true), ('PROBLEM_SETTER','content.premium.read',true);
