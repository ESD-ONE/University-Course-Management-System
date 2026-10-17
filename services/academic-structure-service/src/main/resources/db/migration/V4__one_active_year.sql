-- docs/SYSTEM_ARCHITECTURE_REPORT.md Phase 7 / section 8.1: guarantee at most one
-- ACTIVE academic year at a time — previously only the enum value was checked, not
-- cardinality (gap G8).
CREATE UNIQUE INDEX idx_academic_years_one_active
    ON academic_years ((true)) WHERE status = 'ACTIVE';
