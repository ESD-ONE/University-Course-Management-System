-- Reverts the V4 "at most one ACTIVE year" constraint: it was wrong. Degree programs
-- run 3-4 years, so students enrolled in different years of study are concurrently
-- active across multiple academic years at once (e.g. a 2024/2025 fresher and a
-- 2022/2023 final-year student both have live coursework today). There is no
-- cardinality constraint on how many academic years are ACTIVE at once — only
-- ARCHIVED years are read-only, enforced in the service layer, not the schema.
DROP INDEX IF EXISTS idx_academic_years_one_active;
