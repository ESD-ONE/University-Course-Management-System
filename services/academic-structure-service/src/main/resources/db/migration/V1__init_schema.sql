-- Academic Structure Service schema — database "ucms", schema "academic"
-- (one shared database, one schema per service — see DATA_GOVERNANCE.md).
-- Matches docs/architecture/tables-02-academic-structure.mmd exactly.
--
-- pgcrypto (for gen_random_uuid()) is created once at the database level by
-- infra/init-schemas.sh, run as the admin role — this service's own role is
-- schema-scoped and deliberately lacks CREATE EXTENSION privilege.

CREATE TABLE academic_years (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name            varchar(50)  NOT NULL UNIQUE,
    start_date      date,
    end_date        date,
    status          varchar(20)  NOT NULL CHECK (status IN ('UPCOMING','ACTIVE','ARCHIVED')),
    version         integer      NOT NULL DEFAULT 0,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      uuid,
    deleted_at      timestamptz,
    deleted_by      uuid
);

CREATE TABLE semesters (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    academic_year_id        uuid NOT NULL REFERENCES academic_years(id),
    name                    varchar(50) NOT NULL,
    sequence                int         NOT NULL,
    enrollment_open_at      timestamptz,
    enrollment_close_at     timestamptz,
    status                  varchar(20) NOT NULL CHECK (status IN ('UPCOMING','ACTIVE','ARCHIVED')),
    version                 integer     NOT NULL DEFAULT 0,
    created_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_at              timestamptz NOT NULL DEFAULT now(),
    updated_by              uuid,
    deleted_at              timestamptz,
    deleted_by              uuid,
    UNIQUE (academic_year_id, name)
);

CREATE TABLE degree_programs (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code            varchar(20)  NOT NULL UNIQUE,
    name            varchar(200) NOT NULL,
    faculty_id      uuid         NOT NULL,
    duration_years  int          NOT NULL,
    total_credits   int          NOT NULL,
    status          varchar(20)  NOT NULL CHECK (status IN ('ACTIVE','INACTIVE')),
    version         integer      NOT NULL DEFAULT 0,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      uuid,
    deleted_at      timestamptz,
    deleted_by      uuid
);

CREATE TABLE course_catalog (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code             varchar(20)  NOT NULL UNIQUE,
    name             varchar(200) NOT NULL,
    default_credits  int          NOT NULL,
    version          integer      NOT NULL DEFAULT 0,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    created_by       uuid,
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    updated_by       uuid,
    deleted_at       timestamptz,
    deleted_by       uuid
);

-- No soft delete — matches the architecture diagram exactly (a curriculum entry is
-- un-linked, not soft-deleted, same pattern as Identity & Access's role_permissions).
CREATE TABLE program_courses (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    degree_program_id   uuid NOT NULL REFERENCES degree_programs(id),
    catalog_course_id   uuid NOT NULL REFERENCES course_catalog(id),
    requirement_type    varchar(20) NOT NULL CHECK (requirement_type IN ('CORE','ELECTIVE')),
    recommended_year    int NOT NULL,
    recommended_semester int NOT NULL,
    credit_value        int NOT NULL,
    version             integer NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_at          timestamptz NOT NULL DEFAULT now(),
    updated_by          uuid,
    UNIQUE (degree_program_id, catalog_course_id)
);

CREATE TABLE courses (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    catalog_course_id   uuid NOT NULL REFERENCES course_catalog(id),
    academic_year_id    uuid NOT NULL REFERENCES academic_years(id),
    semester_id         uuid NOT NULL REFERENCES semesters(id),
    lecturer_id         uuid NOT NULL,
    capacity            int  NOT NULL,
    status              varchar(20) NOT NULL CHECK (status IN ('DRAFT','PUBLISHED')),
    version             integer NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_at          timestamptz NOT NULL DEFAULT now(),
    updated_by          uuid,
    deleted_at          timestamptz,
    deleted_by          uuid
);

CREATE INDEX idx_semesters_academic_year ON semesters(academic_year_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_program_courses_program ON program_courses(degree_program_id);
CREATE INDEX idx_courses_lecturer ON courses(lecturer_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_courses_catalog ON courses(catalog_course_id) WHERE deleted_at IS NULL;
