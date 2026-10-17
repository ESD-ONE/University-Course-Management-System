package com.ucms.identity.domain;

/**
 * Hierarchy, broadest to narrowest: DEPARTMENT > DEGREE_PROGRAM > COURSE > COURSE_MODULE.
 * GLOBAL is unscoped (scope_id is null) — used for the Administrator role.
 * See docs/SYSTEM_ARCHITECTURE_REPORT.md section 5.
 */
public enum ScopeType {
    GLOBAL,
    DEPARTMENT,
    DEGREE_PROGRAM,
    COURSE,
    COURSE_MODULE
}
