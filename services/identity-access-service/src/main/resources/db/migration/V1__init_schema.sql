-- Identity & Access Service schema — auth_db (identity_db)
-- Audit standard per docs/architecture/DATA_GOVERNANCE.md.
-- Exceptions honoured exactly as drawn in docs/architecture/tables-01-identity-access.mmd:
--   permissions / role_permissions carry the audit block without soft delete;
--   user_sessions is ephemeral (created_at only).

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE roles (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name            varchar(100) NOT NULL,
    kind            varchar(20)  NOT NULL CHECK (kind IN ('ADMIN','LECTURER','STUDENT')),
    version         integer      NOT NULL DEFAULT 0,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      uuid,
    deleted_at      timestamptz,
    deleted_by      uuid
);

CREATE TABLE users (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name            varchar(200) NOT NULL,
    email           varchar(255) NOT NULL UNIQUE,
    password_hash   varchar(255) NOT NULL,
    role_id         uuid         NOT NULL REFERENCES roles(id),
    status          varchar(20)  NOT NULL CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED')),
    version         integer      NOT NULL DEFAULT 0,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      uuid REFERENCES users(id),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      uuid REFERENCES users(id),
    deleted_at      timestamptz,
    deleted_by      uuid REFERENCES users(id)
);

ALTER TABLE roles ADD CONSTRAINT fk_roles_created_by FOREIGN KEY (created_by) REFERENCES users(id);
ALTER TABLE roles ADD CONSTRAINT fk_roles_updated_by FOREIGN KEY (updated_by) REFERENCES users(id);
ALTER TABLE roles ADD CONSTRAINT fk_roles_deleted_by FOREIGN KEY (deleted_by) REFERENCES users(id);

CREATE TABLE permissions (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    module          varchar(100) NOT NULL,
    action_key      varchar(100) NOT NULL,
    version         integer      NOT NULL DEFAULT 0,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      uuid REFERENCES users(id),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      uuid REFERENCES users(id),
    UNIQUE (module, action_key)
);

CREATE TABLE role_permissions (
    role_id         uuid NOT NULL REFERENCES roles(id),
    permission_id   uuid NOT NULL REFERENCES permissions(id),
    granted         boolean      NOT NULL DEFAULT true,
    version         integer      NOT NULL DEFAULT 0,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      uuid REFERENCES users(id),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      uuid REFERENCES users(id),
    PRIMARY KEY (role_id, permission_id)
);

-- Ephemeral: hard-deleted on logout/expiry, no version/soft-delete (documented exception).
CREATE TABLE user_sessions (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid NOT NULL REFERENCES users(id),
    token_hash      varchar(255) NOT NULL,
    expires_at      timestamptz  NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_role_id ON users(role_id);
CREATE INDEX idx_users_email ON users(email) WHERE deleted_at IS NULL;
CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);
CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_expires_at ON user_sessions(expires_at);
