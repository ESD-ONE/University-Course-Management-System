-- Assignment & Grading Service schema — database "ucms", schema "assignment_grading".
-- Matches docs/architecture/tables-04-assignment-grading.mmd, with these deliberate
-- differences:
--   * assignments.course_id is course_offering_id (a per-year course_academic_years
--     row in academic-structure-service — see enrollment-service's V1 for why).
--   * files: the diagram only has submissions.file_name. The bytes live in MinIO, so the
--     object key, content type and size are stored alongside it; the same for an
--     optional assignment brief (the wireframe's "📎 assignment-2-brief.pdf").
-- Cross-schema ids (students, lecturers, offerings) are plain uuids, no FK.

CREATE TABLE assignments (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    course_offering_id  uuid          NOT NULL,
    title               varchar(200)  NOT NULL,
    description         text,
    due_date            timestamptz   NOT NULL,
    max_marks           integer       NOT NULL CHECK (max_marks > 0),
    status              varchar(20)   NOT NULL CHECK (status IN ('DRAFT','OPEN','CLOSED')),
    brief_file_name     varchar(255),
    brief_object_key    varchar(500),
    version             integer       NOT NULL DEFAULT 0,
    created_at          timestamptz   NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    updated_by          uuid,
    deleted_at          timestamptz,
    deleted_by          uuid
);

CREATE INDEX idx_assignments_offering ON assignments (course_offering_id) WHERE deleted_at IS NULL;

-- No soft delete (diagram has none): a submission is replaced in place on resubmission,
-- version-guarded against racing resubmits.
CREATE TABLE submissions (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id   uuid          NOT NULL REFERENCES assignments(id),
    student_id      uuid          NOT NULL,
    file_name       varchar(255)  NOT NULL,
    object_key      varchar(500)  NOT NULL,
    content_type    varchar(150),
    size_bytes      bigint        NOT NULL,
    submitted_at    timestamptz   NOT NULL,
    status          varchar(20)   NOT NULL CHECK (status IN ('SUBMITTED','GRADED')),
    version         integer       NOT NULL DEFAULT 0,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    updated_by      uuid,
    CONSTRAINT uq_submission_per_student UNIQUE (assignment_id, student_id)
);

CREATE TABLE grades (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id  uuid          NOT NULL UNIQUE REFERENCES submissions(id),
    marks          numeric(7,2)  NOT NULL CHECK (marks >= 0),
    feedback       text,
    graded_by      uuid          NOT NULL,
    graded_at      timestamptz   NOT NULL,
    version        integer       NOT NULL DEFAULT 0,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    created_by     uuid,
    updated_at     timestamptz   NOT NULL DEFAULT now(),
    updated_by     uuid
);
