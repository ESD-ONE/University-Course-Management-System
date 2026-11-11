-- Course delivery (docs/LEARNING_HUB_COURSE_CONTENT_ARCHITECTURE.md sections 7-8): the
-- week-by-week content of one course offering — lecture sessions and materials — plus
-- assignment placement/weighting, held grades and an append-only grade history.
-- The week is the organizing unit; "month-wise" is derived from course_weeks.start_date.
-- Everything hangs off the offering (course_academic_years.id), never the course template,
-- so one academic year's content never shows in another's.

CREATE TABLE course_weeks (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    course_offering_id  uuid          NOT NULL,
    week_number         integer       NOT NULL CHECK (week_number BETWEEN 1 AND 60),
    start_date          date          NOT NULL,
    end_date            date          NOT NULL,
    title               varchar(200),
    status              varchar(20)   NOT NULL CHECK (status IN ('DRAFT','PUBLISHED')),
    version             integer       NOT NULL DEFAULT 0,
    created_at          timestamptz   NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    updated_by          uuid,
    deleted_at          timestamptz,
    deleted_by          uuid,
    CHECK (end_date >= start_date)
);
CREATE UNIQUE INDEX uq_course_weeks_number ON course_weeks (course_offering_id, week_number) WHERE deleted_at IS NULL;

CREATE TABLE lecture_sessions (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    course_offering_id  uuid          NOT NULL,
    week_id             uuid          NOT NULL REFERENCES course_weeks(id),
    title               varchar(200)  NOT NULL,
    description         text,
    starts_at           timestamptz   NOT NULL,
    ends_at             timestamptz   NOT NULL,
    delivery_mode       varchar(20)   NOT NULL CHECK (delivery_mode IN ('ONLINE','PHYSICAL','HYBRID')),
    meeting_url         varchar(1000) CHECK (meeting_url IS NULL OR meeting_url LIKE 'https://%'),
    location            varchar(200),
    recording_url       varchar(1000) CHECK (recording_url IS NULL OR recording_url LIKE 'https://%'),
    status              varchar(20)   NOT NULL CHECK (status IN ('DRAFT','PUBLISHED','CANCELLED')),
    version             integer       NOT NULL DEFAULT 0,
    created_at          timestamptz   NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    updated_by          uuid,
    deleted_at          timestamptz,
    deleted_by          uuid,
    CHECK (ends_at > starts_at)
);
CREATE INDEX idx_lecture_sessions_offering ON lecture_sessions (course_offering_id, starts_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_lecture_sessions_week ON lecture_sessions (week_id) WHERE deleted_at IS NULL;

-- One table for notes, slides, readings, videos and links, discriminated by type. An item
-- is either a stored file (object_key) or an external link — never both, never neither.
CREATE TABLE content_items (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    course_offering_id  uuid          NOT NULL,
    week_id             uuid          NOT NULL REFERENCES course_weeks(id),
    session_id          uuid          REFERENCES lecture_sessions(id),
    type                varchar(20)   NOT NULL CHECK (type IN ('NOTE','SLIDES','READING','VIDEO','LINK')),
    title               varchar(200)  NOT NULL,
    description         text,
    object_key          varchar(500),
    file_name           varchar(255),
    content_type        varchar(150),
    size_bytes          bigint,
    external_url        varchar(1000) CHECK (external_url IS NULL OR external_url LIKE 'https://%'),
    sort_order          integer       NOT NULL DEFAULT 0,
    status              varchar(20)   NOT NULL CHECK (status IN ('DRAFT','PUBLISHED')),
    published_at        timestamptz,
    version             integer       NOT NULL DEFAULT 0,
    created_at          timestamptz   NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    updated_by          uuid,
    deleted_at          timestamptz,
    deleted_by          uuid,
    CHECK ((object_key IS NULL) <> (external_url IS NULL))
);
CREATE INDEX idx_content_items_week ON content_items (week_id, sort_order) WHERE deleted_at IS NULL;

-- Assignments: shown under a week; optional weight for the course total; grades can be
-- held back from students until the lecturer releases them (default: visible on save,
-- as before).
ALTER TABLE assignments ADD COLUMN week_id uuid REFERENCES course_weeks(id);
ALTER TABLE assignments ADD COLUMN weight_percent numeric(5,2) CHECK (weight_percent IS NULL OR (weight_percent > 0 AND weight_percent <= 100));
ALTER TABLE assignments ADD COLUMN grades_hidden boolean NOT NULL DEFAULT false;
CREATE INDEX idx_assignments_week ON assignments (week_id) WHERE deleted_at IS NULL;

-- Append-only: grades are re-graded in place, so this is the only record of what a mark
-- used to be and who changed it. Never updated or deleted.
CREATE TABLE grade_events (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id  uuid          NOT NULL REFERENCES submissions(id),
    action         varchar(20)   NOT NULL CHECK (action IN ('GRADED','REGRADED','RELEASED')),
    marks          numeric(7,2),
    feedback       text,
    object_key     varchar(500),
    actor_id       uuid          NOT NULL,
    occurred_at    timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX idx_grade_events_submission ON grade_events (submission_id, occurred_at);
