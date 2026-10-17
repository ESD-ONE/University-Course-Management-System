-- Splits both DegreeProgram and Course into a reusable "reference" template plus a
-- per-academic-year assignment, so a degree/course can be discontinued (or simply not
-- yet assigned) for a given year without deleting its definition, curriculum, or
-- history. degree_programs and course_catalog-linked courses stay as the stable
-- reference entities ("Reference Degree Details" / course templates); the new junction
-- tables below are what the year-aware "Degree Programs" and "Courses" screens manage.

-- ---------- Degree Program <-> Academic Year assignment ----------

CREATE TABLE degree_program_academic_years (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    degree_program_id   uuid NOT NULL REFERENCES degree_programs(id),
    academic_year_id    uuid NOT NULL REFERENCES academic_years(id),
    duration_years      int,          -- override; falls back to degree_programs.duration_years
    total_credits       int,          -- override; falls back to degree_programs.total_credits
    status              varchar(20) NOT NULL CHECK (status IN ('PLANNED','ACTIVE','DISCONTINUED')),
    version             integer     NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_at          timestamptz NOT NULL DEFAULT now(),
    updated_by          uuid,
    deleted_at          timestamptz,
    deleted_by          uuid,
    UNIQUE (degree_program_id, academic_year_id)
);

CREATE INDEX idx_dpay_year ON degree_program_academic_years(academic_year_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_dpay_program ON degree_program_academic_years(degree_program_id) WHERE deleted_at IS NULL;

-- Seed: every existing reference degree is assigned ACTIVE for the currently-active
-- academic year (2025/2026), matching today's de-facto state before this migration.
INSERT INTO degree_program_academic_years (degree_program_id, academic_year_id, status, created_by, updated_by)
SELECT dp.id, 'a1000000-0000-0000-0000-000000000002', 'ACTIVE',
       '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'
FROM degree_programs dp
WHERE dp.deleted_at IS NULL;

-- ---------- Course becomes a reusable template; year/semester moves to a junction ----------

ALTER TABLE courses ADD COLUMN degree_program_id uuid;
ALTER TABLE courses ADD COLUMN begin_date date;
ALTER TABLE courses ADD COLUMN year_of_study int;

-- Backfill the 3 seeded courses: degree_program_id resolved via curriculum membership
-- (COSC5105/COSC5210/COSC6210 are all in BSc-CS's curriculum); begin_date from the
-- academic year they were previously tied to; year_of_study defaulted to 1 (no prior
-- data to derive this from).
UPDATE courses c
SET degree_program_id = 'a3000000-0000-0000-0000-000000000001',
    begin_date = (SELECT start_date FROM academic_years WHERE id = c.academic_year_id),
    year_of_study = 1
WHERE c.deleted_at IS NULL;

ALTER TABLE courses ALTER COLUMN degree_program_id SET NOT NULL;
ALTER TABLE courses ALTER COLUMN begin_date SET NOT NULL;
ALTER TABLE courses ALTER COLUMN year_of_study SET NOT NULL;
ALTER TABLE courses ADD CONSTRAINT fk_courses_degree_program FOREIGN KEY (degree_program_id) REFERENCES degree_programs(id);

CREATE TABLE course_academic_years (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id           uuid NOT NULL REFERENCES courses(id),
    academic_year_id    uuid NOT NULL REFERENCES academic_years(id),
    semester_id         uuid REFERENCES semesters(id),
    capacity            int,          -- override; falls back to courses.capacity
    status              varchar(20) NOT NULL CHECK (status IN ('DRAFT','PUBLISHED','CANCELLED')),
    version             integer     NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_at          timestamptz NOT NULL DEFAULT now(),
    updated_by          uuid,
    deleted_at          timestamptz,
    deleted_by          uuid,
    UNIQUE (course_id, academic_year_id)
);

CREATE INDEX idx_cay_year ON course_academic_years(academic_year_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_cay_course ON course_academic_years(course_id) WHERE deleted_at IS NULL;

-- Preserve each existing course's current academic_year_id/semester_id/capacity/status
-- as its first year-assignment row before those columns are dropped from courses.
INSERT INTO course_academic_years (course_id, academic_year_id, semester_id, capacity, status, created_by, updated_by)
SELECT id, academic_year_id, semester_id, capacity, status, created_by, updated_by
FROM courses
WHERE deleted_at IS NULL;

ALTER TABLE courses DROP COLUMN academic_year_id;
ALTER TABLE courses DROP COLUMN semester_id;

CREATE INDEX idx_courses_degree_program ON courses(degree_program_id) WHERE deleted_at IS NULL;
