-- A course's lecturer can differ from year to year (the same course template can run
-- in multiple academic years, each taught by someone different) — mirrors the existing
-- capacity/status override pattern on course_academic_years. courses.lecturer_id stays
-- as the template's default (used for self-service "my courses" and as the fallback
-- when no year has overridden it); this column is that fallback's per-year override.
ALTER TABLE course_academic_years ADD COLUMN lecturer_id uuid;
