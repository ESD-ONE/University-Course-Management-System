-- Learning-hub / course-delivery RBAC additions (docs/LEARNING_HUB_COURSE_CONTENT_ARCHITECTURE.md
-- sections 5 and 9): a seeded "Program Head" role, held at DEGREE_PROGRAM scope, and the
-- permission modules that course content, assignments, grading and analytics check.

INSERT INTO roles (name, kind, created_by, updated_by)
SELECT 'Program Head', 'LECTURER', '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'
WHERE NOT EXISTS (SELECT 1 FROM roles r WHERE r.name = 'Program Head' AND r.deleted_at IS NULL);

INSERT INTO permissions (module, action_key, created_by, updated_by)
SELECT m.module, a.action, '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'
FROM (VALUES ('Course Content'),('Assignments'),('Grading'),('Assignment Analytics')) AS m(module)
CROSS JOIN (VALUES ('View'),('Create'),('Edit'),('Delete')) AS a(action)
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.module = m.module AND p.action_key = a.action);

-- Administrator and Lecturer baselines: the whole new matrix. A Lecturer's GLOBAL baseline
-- alone never grants another lecturer's course (services require ownership or a genuinely
-- scoped assignment), so this only matters once the role is held at a narrower scope.
INSERT INTO role_permissions (role_id, permission_id, granted, created_by, updated_by)
SELECT r.id, p.id, true, '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'
FROM roles r CROSS JOIN permissions p
WHERE r.id IN ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002')
  AND p.module IN ('Course Content','Assignments','Grading','Assignment Analytics')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- Student baseline: read-only.
INSERT INTO role_permissions (role_id, permission_id, granted, created_by, updated_by)
SELECT '00000000-0000-0000-0000-000000000003', p.id, true,
       '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'
FROM permissions p
WHERE p.action_key = 'View' AND p.module IN ('Course Content','Assignments')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp
                  WHERE rp.role_id = '00000000-0000-0000-0000-000000000003' AND rp.permission_id = p.id);

-- Program Head baseline: manage the program's courses and everything delivered in them.
INSERT INTO role_permissions (role_id, permission_id, granted, created_by, updated_by)
SELECT r.id, p.id, true, '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'Program Head' AND r.deleted_at IS NULL
  AND ((p.module = 'Degree Programs' AND p.action_key IN ('View','Edit'))
    OR (p.module IN ('Courses','Course Modules','Course Content','Assignments','Grading')
        AND p.action_key IN ('View','Create','Edit','Delete'))
    OR (p.module = 'Assignment Analytics' AND p.action_key = 'View'))
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
