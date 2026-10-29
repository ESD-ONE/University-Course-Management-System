-- Test-only initialization. spring.sql.init.mode defaults to "embedded", so this file
-- is only ever run automatically against the in-memory H2 database used by the "test"
-- profile (see application-test.yml) — never against the real Postgres database, where
-- Flyway's V6__student_id_sequence.sql migration creates the same sequence instead.
--
-- Needed because the "test" profile uses spring.jpa.hibernate.ddl-auto=create-drop with
-- Flyway disabled, so Hibernate (not Flyway) builds the schema from JPA entities alone —
-- a plain SQL sequence like this one has no entity to hang off of, so it wouldn't
-- otherwise exist for UserService.generateUniqueStudentId() (G-16) to call nextval() on.
CREATE SCHEMA IF NOT EXISTS identity_access;
CREATE SEQUENCE IF NOT EXISTS identity_access.student_id_seq START WITH 1 INCREMENT BY 1;
