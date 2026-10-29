-- academic-structure-service's V3 migration references the two seeded departments by
-- fixed UUIDs (the ids they had on the production database when it was written), but
-- V3 here created them with gen_random_uuid(). On production the two already match; on
-- any freshly provisioned database they don't, leaving every seeded degree program and
-- catalog subject pointing at a department that doesn't exist.
--
-- Give the seeded departments those well-known ids wherever they differ. A no-op on
-- production. Safe elsewhere: nothing in this schema references departments.id (the
-- references live in other services, which already use these exact values).

UPDATE departments SET id = 'cada3c63-32df-4f8c-82be-8a896df961d9'
WHERE code = 'DOCS' AND id <> 'cada3c63-32df-4f8c-82be-8a896df961d9'
  AND NOT EXISTS (SELECT 1 FROM departments WHERE id = 'cada3c63-32df-4f8c-82be-8a896df961d9');

UPDATE departments SET id = '4ae82bc4-8797-49ee-8b12-f5ea82a309e7'
WHERE code = 'DOM' AND id <> '4ae82bc4-8797-49ee-8b12-f5ea82a309e7'
  AND NOT EXISTS (SELECT 1 FROM departments WHERE id = '4ae82bc4-8797-49ee-8b12-f5ea82a309e7');
