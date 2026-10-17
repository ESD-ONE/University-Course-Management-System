-- docs/SYSTEM_ARCHITECTURE_REPORT.md Phase 1 / section 14 step 3-4:
-- rename degree_programs.faculty_id -> department_id, and add
-- course_catalog.owning_department_id, both now pointing at Reference Data's real
-- departments table (completed by that service's own V3 migration) instead of a
-- faculty, with existing rows backfilled before the NOT NULL rename takes effect.
--
-- department ids below are the real, already-live reference_data.departments rows
-- (code DOCS / DOM) — plain uuid, "ref Reference Data, no FK", same cross-schema
-- pattern already used for faculty_id.

ALTER TABLE degree_programs RENAME COLUMN faculty_id TO department_id;

UPDATE degree_programs
SET department_id = 'cada3c63-32df-4f8c-82be-8a896df961d9' -- Department of Computer Science (DOCS)
WHERE code IN ('BSc-CS', 'BSc-SE', 'BSc-DS', 'Dip-IT');

ALTER TABLE course_catalog ADD COLUMN owning_department_id uuid;

UPDATE course_catalog
SET owning_department_id = 'cada3c63-32df-4f8c-82be-8a896df961d9' -- Department of Computer Science (DOCS)
WHERE code IN ('COSC5105', 'COSC5210', 'COSC6210', 'COSC6320');

UPDATE course_catalog
SET owning_department_id = '4ae82bc4-8797-49ee-8b12-f5ea82a309e7' -- Department of Mathematics (DOM)
WHERE code IN ('MATH2031');

ALTER TABLE course_catalog ALTER COLUMN owning_department_id SET NOT NULL;

CREATE INDEX idx_course_catalog_owning_department ON course_catalog(owning_department_id) WHERE deleted_at IS NULL;
