package com.ucms.grading.web.dto;

import com.ucms.grading.domain.Assignment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class Dtos {
    private Dtos() {}

    public record CreateAssignmentRequest(@NotNull UUID offeringId, @NotBlank @Size(max = 200) String title,
                                          @Size(max = 10000) String description, @NotNull OffsetDateTime dueDate,
                                          @NotNull @Min(1) @Max(1000) Integer maxMarks, UUID weekId,
                                          @DecimalMin("0.01") @Max(100) BigDecimal weightPercent) {}

    public record UpdateAssignmentRequest(@NotBlank @Size(max = 200) String title, @Size(max = 10000) String description,
                                          @NotNull OffsetDateTime dueDate, @NotNull @Min(1) @Max(1000) Integer maxMarks,
                                          UUID weekId, @DecimalMin("0.01") @Max(100) BigDecimal weightPercent) {}

    public record StatusRequest(@NotNull Assignment.Status status) {}

    /** The my* fields are filled only for a student caller. */
    public record AssignmentResponse(UUID id, UUID offeringId, String courseCode, String courseName, String title,
                                     String description, OffsetDateTime dueDate, int maxMarks, String status,
                                     String briefFileName, long totalStudents, long submittedCount, long gradedCount,
                                     String mySubmissionStatus, OffsetDateTime mySubmittedAt, String myFileName,
                                     BigDecimal myMarks, String myFeedback, UUID weekId, BigDecimal weightPercent,
                                     boolean gradesHidden) {}

    /** One row per enrolled student — including those who haven't submitted. */
    public record SubmissionRow(UUID studentId, String studentName, String studentNumber, UUID submissionId,
                                String fileName, OffsetDateTime submittedAt, boolean late, String status,
                                BigDecimal marks, String feedback) {}

    public record GradeRequest(@NotNull @DecimalMin("0") BigDecimal marks, @Size(max = 5000) String feedback) {}

    public record BulkGradeItem(@NotNull UUID submissionId, @NotNull @DecimalMin("0") BigDecimal marks,
                                @Size(max = 5000) String feedback) {}

    public record BulkGradeRequest(@NotEmpty List<@Valid BulkGradeItem> grades) {}

    public record GradeResponse(UUID gradeId, UUID submissionId, BigDecimal marks, int maxMarks, String feedback,
                                UUID gradedBy, OffsetDateTime gradedAt) {}

    public record MySubmissionResponse(UUID submissionId, String fileName, OffsetDateTime submittedAt, String status,
                                       BigDecimal marks, int maxMarks, String feedback, OffsetDateTime gradedAt) {}

    public record CourseGradeRow(UUID offeringId, String courseCode, String courseName, int credits,
                                 String academicYearName, String academicYearStatus, String semesterName,
                                 int gradedAssignments, Double percentage, String letter, Double points) {}

    public record GradeHistoryResponse(List<CourseGradeRow> courses, Double gpa, int creditsGraded) {}

    public record OfferingSummaryResponse(UUID offeringId, String courseCode, String courseName, long totalStudents,
                                          int assignments, long submitted, long graded, long pending,
                                          Double classAverage) {}

    public record CourseResultRow(UUID offeringId, UUID studentId, double percentage, int gradedAssignments) {}

    public record GradesVisibilityRequest(@NotNull boolean hidden) {}

    public record StudentScoreRow(UUID studentId, String studentName, BigDecimal marks, double percentage) {}

    public record AssignmentAnalyticsResponse(long totalStudents, long submitted, long onTime, long late, long graded,
                                               Double average, Double median, Double min, Double max,
                                               List<Integer> histogram, List<StudentScoreRow> topPerformers,
                                               List<StudentScoreRow> needsAttention) {}

    public record AssignmentTrendPoint(UUID assignmentId, String title, OffsetDateTime dueDate, Double classAverage,
                                       long submitted, long graded, long totalStudents, BigDecimal weightPercent) {}

    public record AtRiskRow(UUID studentId, String studentName, double averagePercentage, long missingSubmissions) {}

    public record OfferingAnalyticsResponse(List<AssignmentTrendPoint> assignments, Double weightedCourseAverage,
                                            Double classAverage, List<AtRiskRow> atRisk) {}
}
