-- Student ID is an identifier on the account itself (Identity & Access's own
-- data), not academic data — degree program, year, etc. belong to the
-- Academic Structure service, which doesn't exist yet. Nullable: only
-- STUDENT-role users get one, generated server-side on creation.
-- See docs/SYSTEM_GAP_ANALYSIS_AND_IMPLEMENTATION_PLAN.md section C.5.

ALTER TABLE users ADD COLUMN student_id varchar(20) UNIQUE;
