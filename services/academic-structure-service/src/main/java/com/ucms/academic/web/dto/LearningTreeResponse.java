package com.ucms.academic.web.dto;

import java.util.List;
import java.util.UUID;

/** The Learning Hub's nav tree: academic years → their assigned degree programs → the
 * courses (offerings) in them that the caller can actually access. Built by grouping
 * CourseOfferingResponse rows already visible to the caller — see
 * CourseOfferingQueryService#accessible / EnrollmentLearningTree equivalents, and
 * docs/LEARNING_HUB_COURSE_CONTENT_ARCHITECTURE.md section 4.4. */
public record LearningTreeResponse(List<YearNode> years) {

    public record YearNode(UUID academicYearId, String academicYearName, String academicYearStatus,
                           List<ProgramNode> programs) {}

    public record ProgramNode(UUID degreeProgramId, String degreeProgramCode, String degreeProgramName,
                              List<OfferingNode> offerings) {}

    public record OfferingNode(UUID offeringId, String code, String name, int credits, String requirementType,
                               int yearOfStudy, String status, UUID lecturerId, boolean openForEnrollment) {}
}
