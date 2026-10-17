-- Completes docs/architecture/tables-07-reference-data.mmd's FACULTIES ||--o{ DEPARTMENTS,
-- which was drawn in the original architecture but never implemented. "Department of
-- Mathematics" previously existed only as a faked faculties row (code DOM) — this
-- migration moves it into a real department and removes the fake row. See
-- docs/SYSTEM_ARCHITECTURE_REPORT.md section 2.3 / 14.

CREATE TABLE departments (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id      uuid NOT NULL REFERENCES faculties(id),
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

CREATE INDEX idx_departments_faculty ON departments(faculty_id) WHERE deleted_at IS NULL;

-- "Faculty of Science" was created ad-hoc via the live API during earlier manual testing
-- in some environments but was never added to a migration — insert it idempotently so a
-- fresh environment (replaying V1+V2+V3 only) ends up identical to the already-live one.
INSERT INTO faculties (name, code, status, created_by, updated_by)
SELECT 'Faculty of Science', 'FOS', 'ACTIVE',
       '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'
WHERE NOT EXISTS (SELECT 1 FROM faculties WHERE code = 'FOS');

-- Migrate the faked "Department of Mathematics" faculty row into a real department under
-- Faculty of Science.
INSERT INTO departments (faculty_id, name, code, status, created_by, updated_by)
SELECT (SELECT id FROM faculties WHERE code = 'FOS'), 'Department of Mathematics', 'DOM', 'ACTIVE',
       '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'
WHERE EXISTS (SELECT 1 FROM faculties WHERE code = 'DOM')
  AND NOT EXISTS (SELECT 1 FROM departments WHERE code = 'DOM');

DELETE FROM faculties WHERE code = 'DOM';

-- Department of Computer Science under Faculty of Computing — owns the existing seeded
-- degree programs (BSc-CS/SE/DS, Dip-IT) and the COSC* catalog subjects once Academic
-- Structure's own migration backfills department_id/owning_department_id.
INSERT INTO departments (faculty_id, name, code, status, created_by, updated_by)
SELECT (SELECT id FROM faculties WHERE code = 'FOC'), 'Department of Computer Science', 'DOCS', 'ACTIVE',
       '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE code = 'DOCS');
