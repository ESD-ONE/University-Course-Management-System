-- Seed the 3 roles and a bootstrap admin account.
-- Password for the seed admin is "ChangeMe123!" (bcrypt, cost 10) — rotate immediately after first login.
-- $2a$10$m9X4XaBFGUPhws.fC2rkded59.5d2gfeKeHck8eHKf9kKn3us6Dpe == bcrypt("ChangeMe123!"), self-verified at generation time.

INSERT INTO roles (id, name, kind, created_by, updated_by) VALUES
    ('00000000-0000-0000-0000-000000000001', 'Administrator', 'ADMIN', NULL, NULL),
    ('00000000-0000-0000-0000-000000000002', 'Lecturer', 'LECTURER', NULL, NULL),
    ('00000000-0000-0000-0000-000000000003', 'Student', 'STUDENT', NULL, NULL);

INSERT INTO users (id, name, email, password_hash, role_id, status, created_by, updated_by) VALUES
    ('00000000-0000-0000-0000-00000000000a', 'Seed Administrator', 'admin@ucms.local',
     '$2a$10$m9X4XaBFGUPhws.fC2rkded59.5d2gfeKeHck8eHKf9kKn3us6Dpe',
     '00000000-0000-0000-0000-000000000001', 'ACTIVE', NULL, NULL);

UPDATE roles SET created_by = '00000000-0000-0000-0000-00000000000a',
                 updated_by = '00000000-0000-0000-0000-00000000000a'
WHERE created_by IS NULL;

INSERT INTO permissions (module, action_key, created_by, updated_by) VALUES
    ('users', 'manage', '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'),
    ('roles', 'manage', '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a');

INSERT INTO role_permissions (role_id, permission_id, granted, created_by, updated_by)
SELECT '00000000-0000-0000-0000-000000000001', id, true,
       '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'
FROM permissions;
