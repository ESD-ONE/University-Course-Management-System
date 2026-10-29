package com.ucms.analytics.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class Dtos {
    private Dtos() {}

    public record Point(String label, double value) {}

    public record TopCourse(UUID offeringId, String code, String name, String lecturerName, long enrolled) {}

    public record RecentRegistration(String studentName, String targetLabel, String actorName, String actorRole,
                                     String action, OffsetDateTime createdAt) {}

    public record AdminKpis(long totalStudents, long activeCourses, long degreePrograms, long activeEnrollments,
                            long waitlisted) {}

    public record AdminDashboard(AdminKpis kpis, List<Point> enrollmentTrend, List<Point> studentsByProgram,
                                 List<TopCourse> topCourses, List<RecentRegistration> recentRegistrations,
                                 OffsetDateTime computedAt) {}

    public record LecturerCourse(UUID offeringId, String code, String name, String academicYearName, String semesterName,
                                 long enrolled, int capacity, String status, int assignments, long graded,
                                 long submitted, Double classAverage) {}

    public record LecturerDashboard(int myCourses, long totalStudents, long toGrade, Double averagePerformance,
                                    long graded, long pending, long notSubmitted, List<LecturerCourse> courses) {}

    public record StudentCourse(UUID offeringId, String code, String name, String lecturerName, String requirementType,
                                Integer credits, Double percentage, String letter) {}

    public record StudentDashboard(String programName, String programCode, Integer yearOfStudy, String intakeYear,
                                   int enrolledCourses, int creditsCompleted, Integer creditsRequired, Double gpa,
                                   int upcomingDeadlines, List<Point> gpaTrend, List<StudentCourse> courses) {}

    public record ReportResponse(UUID id, String name, String description, String format, OffsetDateTime lastGeneratedAt,
                                 boolean available) {}

    public record ScheduleRequest(@NotNull UUID reportId,
                                  @NotNull @Pattern(regexp = "DAILY|WEEKLY|MONTHLY") String frequency,
                                  @NotNull @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d") String time,
                                  @Pattern(regexp = "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY") String dayOfWeek,
                                  @Min(1) @Max(28) Integer dayOfMonth,
                                  @NotBlank @Size(max = 1000) String recipients) {}

    public record ScheduleStatusRequest(@NotNull @Pattern(regexp = "ACTIVE|PAUSED") String status) {}

    public record ScheduleResponse(UUID id, UUID reportId, String reportName, String frequency, String frequencyLabel,
                                   String recipients, String status, OffsetDateTime nextRunAt) {}
}
