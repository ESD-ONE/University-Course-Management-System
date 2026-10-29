package com.ucms.academic.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One course offering (a course_academic_years row) flattened with everything other
 * services need to act on it without further lookups: catalog code/name, the credits and
 * Core/Elective status it carries in its program's curriculum, the resolved capacity and
 * lecturer (per-year override, else the course template's default), and whether it's
 * currently open for enrollment. Enrollment, Assignment & Grading, and Analytics all key
 * their own records by this offering id.
 */
public record CourseOfferingResponse(UUID id, UUID courseId, UUID catalogCourseId, String code, String name,
                                      int credits, String requirementType,
                                      UUID degreeProgramId, String degreeProgramCode, String degreeProgramName,
                                      UUID departmentId, int yearOfStudy,
                                      UUID academicYearId, String academicYearName, String academicYearStatus,
                                      UUID semesterId, String semesterName,
                                      OffsetDateTime enrollmentOpenAt, OffsetDateTime enrollmentCloseAt,
                                      int capacity, UUID lecturerId, String status, boolean openForEnrollment) {}
