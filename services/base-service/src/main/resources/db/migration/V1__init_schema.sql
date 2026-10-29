-- Reference Data Service schema — database "ucms", schema "reference_data"
-- (one shared database, one schema per service — see DATA_GOVERNANCE.md).
-- Matches docs/architecture/tables-07-reference-data.mmd.
--
-- pgcrypto (for gen_random_uuid()) is created once at the database level by
-- infra/init-schemas.sh, run as the admin role — this service's own role is
-- schema-scoped and deliberately lacks CREATE EXTENSION privilege.

CREATE TABLE faculties (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name            varchar(200) NOT NULL,
    code            varchar(20)  NOT NULL UNIQUE,
    status          varchar(20)  NOT NULL CHECK (status IN ('ACTIVE','INACTIVE')),
    version         integer      NOT NULL DEFAULT 0,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      uuid,
    deleted_at      timestamptz,
    deleted_by      uuid
);

CREATE TABLE grading_scales (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name            varchar(200) NOT NULL,
    bands           text         NOT NULL,
    status          varchar(20)  NOT NULL CHECK (status IN ('ACTIVE','INACTIVE')),
    version         integer      NOT NULL DEFAULT 0,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      uuid,
    deleted_at      timestamptz,
    deleted_by      uuid
);

CREATE TABLE course_categories (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name            varchar(200) NOT NULL,
    description     text         NOT NULL,
    status          varchar(20)  NOT NULL CHECK (status IN ('ACTIVE','INACTIVE')),
    version         integer      NOT NULL DEFAULT 0,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      uuid,
    deleted_at      timestamptz,
    deleted_by      uuid
);

CREATE INDEX idx_faculties_status ON faculties(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_grading_scales_status ON grading_scales(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_course_categories_status ON course_categories(status) WHERE deleted_at IS NULL;
