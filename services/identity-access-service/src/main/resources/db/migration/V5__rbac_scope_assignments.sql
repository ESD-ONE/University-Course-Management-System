-- docs/SYSTEM_ARCHITECTURE_REPORT.md Phase 2: the scope-assignment layer on top of the
-- existing roles/permissions/role_permissions tables (migrated since V1, never used
-- until now). See section 5.2 for the design.

CREATE TABLE role_scope_assignments (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid NOT NULL REFERENCES users(id),
    role_id         uuid NOT NULL REFERENCES roles(id),
    scope_type      varchar(20) NOT NULL CHECK (scope_type IN
                        ('GLOBAL','DEPARTMENT','DEGREE_PROGRAM','COURSE','COURSE_MODULE')),
    scope_id        uuid,                 -- "ref Reference Data or Academic Structure, no FK"
    version         integer     NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid REFERENCES users(id),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    updated_by      uuid REFERENCES users(id),
    deleted_at      timestamptz,
    deleted_by      uuid REFERENCES users(id),
    CHECK ((scope_type = 'GLOBAL') = (scope_id IS NULL))
);

CREATE INDEX idx_rsa_user ON role_scope_assignments(user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_rsa_scope ON role_scope_assignments(scope_type, scope_id) WHERE deleted_at IS NULL;

-- No soft delete — same documented exception as role_permissions (AuditableNoSoftDelete):
-- an override is removed outright, not soft-deleted.
CREATE TABLE assignment_permission_overrides (
    assignment_id   uuid NOT NULL REFERENCES role_scope_assignments(id),
    permission_id   uuid NOT NULL REFERENCES permissions(id),
    granted         boolean     NOT NULL,
    version         integer     NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid REFERENCES users(id),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    updated_by      uuid REFERENCES users(id),
    PRIMARY KEY (assignment_id, permission_id)
);

-- Permission catalog additions (docs/SYSTEM_ARCHITECTURE_REPORT.md section 5.7). The
-- existing ('users','manage') / ('roles','manage') rows from V2 are left untouched —
-- these are a parallel, more granular Module x [View/Create/Edit/Delete] matrix matching
-- the wireframe's adminRoles() permission grid exactly.
INSERT INTO permissions (module, action_key, created_by, updated_by)
SELECT m.module, a.action, '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'
FROM (VALUES ('Degree Programs'),('Courses'),('Course Modules'),('Departments'),('Grading Scales'),
             ('Users'),('Reference Data')) AS m(module)
CROSS JOIN (VALUES ('View'),('Create'),('Edit'),('Delete')) AS a(action)
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.module = m.module AND p.action_key = a.action);

-- Administrator's baseline: everything in the new matrix.
INSERT INTO role_permissions (role_id, permission_id, granted, created_by, updated_by)
SELECT '00000000-0000-0000-0000-000000000001', p.id, true,
       '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'
FROM permissions p
WHERE p.module IN ('Degree Programs','Courses','Course Modules','Departments','Grading Scales','Users','Reference Data')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = '00000000-0000-0000-0000-000000000001' AND rp.permission_id = p.id
  );

-- Lecturer's baseline: view most academic data, manage their own courses/modules.
INSERT INTO role_permissions (role_id, permission_id, granted, created_by, updated_by)
SELECT '00000000-0000-0000-0000-000000000002', p.id, true,
       '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'
FROM permissions p
WHERE ((p.module = 'Degree Programs' AND p.action_key = 'View')
   OR (p.module = 'Courses' AND p.action_key IN ('View','Create','Edit'))
   OR (p.module = 'Course Modules' AND p.action_key IN ('View','Create','Edit','Delete'))
   OR (p.module = 'Grading Scales' AND p.action_key = 'View')
   OR (p.module = 'Reference Data' AND p.action_key = 'View'))
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = '00000000-0000-0000-0000-000000000002' AND rp.permission_id = p.id
  );

-- Student's baseline: read-only.
INSERT INTO role_permissions (role_id, permission_id, granted, created_by, updated_by)
SELECT '00000000-0000-0000-0000-000000000003', p.id, true,
       '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'
FROM permissions p
WHERE p.action_key = 'View' AND p.module IN ('Degree Programs','Courses','Course Modules')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = '00000000-0000-0000-0000-000000000003' AND rp.permission_id = p.id
  );

-- Backfill: every existing user gets one GLOBAL-scope assignment matching their current
-- role, so the new model starts out behaviorally equivalent to today — no department/
-- program/course/module scoping exists yet until an admin explicitly creates one.
-- See docs/SYSTEM_ARCHITECTURE_REPORT.md section 14 step 5.
INSERT INTO role_scope_assignments (user_id, role_id, scope_type, scope_id, created_by, updated_by)
SELECT u.id, u.role_id, 'GLOBAL', NULL, '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'
FROM users u
WHERE u.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM role_scope_assignments rsa WHERE rsa.user_id = u.id AND rsa.scope_type = 'GLOBAL'
  );
