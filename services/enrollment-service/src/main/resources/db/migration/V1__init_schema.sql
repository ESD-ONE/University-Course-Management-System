-- Enrollment Service schema — database "ucms", schema "enrollment".
-- Matches docs/architecture/tables-03-enrollment.mmd, with one deliberate rename:
-- the diagram's course_enrollments.course_id is course_offering_id here. Since
-- academic-structure-service's V5 migration split courses into a reusable template
-- (courses) and a per-academic-year offering (course_academic_years), a student enrolls
-- in a specific year's offering, not the template — the column name says so.
-- Every *_id below is a cross-schema reference by plain uuid, no FK (see
-- DATA_GOVERNANCE.md): students live in identity_access, programs/offerings in academic.

CREATE TABLE student_program_enrollments (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id               uuid        NOT NULL,
    degree_program_id        uuid        NOT NULL,
    intake_academic_year_id  uuid        NOT NULL,
    current_year_of_study    integer     NOT NULL CHECK (current_year_of_study BETWEEN 1 AND 10),
    status                   varchar(20) NOT NULL CHECK (status IN ('ACTIVE','DEFERRED','WITHDRAWN','GRADUATED')),
    version                  integer     NOT NULL DEFAULT 0,
    created_at               timestamptz NOT NULL DEFAULT now(),
    created_by               uuid,
    updated_at               timestamptz NOT NULL DEFAULT now(),
    updated_by               uuid,
    deleted_at               timestamptz,
    deleted_by               uuid
);

-- A student studies one degree program at a time; transfer = withdraw, then register.
CREATE UNIQUE INDEX uq_spe_one_active_per_student
    ON student_program_enrollments (student_id) WHERE status = 'ACTIVE' AND deleted_at IS NULL;
CREATE INDEX idx_spe_program ON student_program_enrollments (degree_program_id) WHERE deleted_at IS NULL;

CREATE TABLE course_enrollments (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id          uuid        NOT NULL,
    course_offering_id  uuid        NOT NULL,
    enrolled_on         date        NOT NULL,
    status              varchar(20) NOT NULL CHECK (status IN ('ENROLLED','WAITLISTED','DROPPED','COMPLETED')),
    version             integer     NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_at          timestamptz NOT NULL DEFAULT now(),
    updated_by          uuid,
    deleted_at          timestamptz,
    deleted_by          uuid
);

-- Dropping soft-deletes the row, so a dropped student can enroll again later; a live
-- row (enrolled or waitlisted) can only exist once per student per offering.
CREATE UNIQUE INDEX uq_ce_live_per_student_offering
    ON course_enrollments (student_id, course_offering_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_ce_offering_status ON course_enrollments (course_offering_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_ce_student ON course_enrollments (student_id) WHERE deleted_at IS NULL;

-- Append-only audit trail (no update/delete columns, same documented exception as other
-- log tables). target_id is polymorphic: a degree program id or a course offering id.
CREATE TABLE registration_log (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id   uuid        NOT NULL,
    target_type  varchar(20) NOT NULL CHECK (target_type IN ('PROGRAM','COURSE')),
    target_id    uuid        NOT NULL,
    actor_id     uuid        NOT NULL,
    actor_role   varchar(20) NOT NULL,
    action       varchar(40) NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_reglog_created ON registration_log (created_at DESC);
CREATE INDEX idx_reglog_student ON registration_log (student_id);
