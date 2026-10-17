package com.ucms.academic.domain;

/**
 * No FULL value yet — that's derived from enrolled-vs-capacity, which needs the
 * Enrollment service's data (doesn't exist yet); see docs/FULL_SYSTEM_GAP_ANALYSIS.md.
 * CANCELLED is a per-year-assignment-only state (CourseAcademicYear) — a course
 * template itself is only ever DRAFT or PUBLISHED.
 */
public enum CourseStatus { DRAFT, PUBLISHED, CANCELLED }
