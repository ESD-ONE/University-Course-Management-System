-- Seed data matching html-wireframes/index.html's DB.reference demo entries, so the
-- real app and the wireframe show the same starting reference data. created_by/
-- updated_by reference the seed admin from Identity & Access's identity_access.users —
-- a plain uuid, no FK (cross-schema, see DATA_GOVERNANCE.md's FK rule), matching that
-- service's own seeded admin id exactly.

INSERT INTO faculties (name, code, status, created_by, updated_by) VALUES
    ('Faculty of Computing', 'FOC', 'ACTIVE', '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'),
    ('Faculty of Engineering', 'FOE', 'ACTIVE', '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'),
    ('Faculty of Business', 'FOB', 'ACTIVE', '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'),
    ('Department of Mathematics', 'DOM', 'ACTIVE', '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a');

INSERT INTO grading_scales (name, bands, status, created_by, updated_by) VALUES
    ('Standard Percentage', 'A: 70-100 · B: 60-69 · C: 50-59 · D: 40-49 · F: 0-39', 'ACTIVE', '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'),
    ('Pass / Fail', 'Pass: 50-100 · Fail: 0-49', 'ACTIVE', '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a');

INSERT INTO course_categories (name, description, status, created_by, updated_by) VALUES
    ('Core', 'Mandatory for program completion', 'ACTIVE', '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a'),
    ('Elective', 'Optional, counts toward elective credit', 'ACTIVE', '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000a');
