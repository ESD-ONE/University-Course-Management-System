package com.ucms.enrollment.web.dto;

import com.ucms.enrollment.domain.ProgramEnrollmentStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Request/response shapes for every Enrollment endpoint, in one place. */
public final class Dtos {
    private Dtos() {}

    public record CreateProgramEnrollmentRequest(@NotNull UUID studentId, @NotNull UUID degreeProgramId,
                                                 @NotNull UUID intakeAcademicYearId,
                                                 @NotNull @Min(1) @Max(10) Integer currentYearOfStudy) {}

    public record UpdateProgramEnrollmentRequest(@Min(1) @Max(10) Integer currentYearOfStudy,
                                                 ProgramEnrollmentStatus status) {}

    public record ProgramEnrollmentResponse(UUID id, UUID studentId, String studentName, String studentNumber,
                                            UUID degreeProgramId, String degreeProgramCode, String degreeProgramName,
                                            Integer durationYears, Integer totalCredits,
                                            UUID intakeAcademicYearId, String intakeAcademicYearName,
                                            int currentYearOfStudy, String status, OffsetDateTime createdAt) {}

    /** studentId: omitted by a student enrolling themselves; required when an admin or
     *  lecturer registers someone. joinWaitlistIfFull: the "join waitlist?" follow-up. */
    public record EnrollRequest(@NotNull UUID offeringId, UUID studentId, boolean joinWaitlistIfFull) {}

    public record CourseEnrollmentResponse(UUID id, UUID studentId, String studentName, String studentNumber,
                                           String studentEmail, String studentProgramName, Integer studentYearOfStudy,
                                           UUID offeringId, String courseCode, String courseName, Integer credits,
                                           String requirementType, String academicYearName, String semesterName,
                                           UUID lecturerId, String lecturerName, LocalDate enrolledOn, String status) {}

    public record CatalogEntryResponse(UUID offeringId, String code, String name, int credits, String requirementType,
                                       UUID degreeProgramId, String degreeProgramCode, String degreeProgramName,
                                       int yearOfStudy, UUID academicYearId, String academicYearName,
                                       String semesterName, UUID lecturerId, String lecturerName,
                                       int capacity, long enrolled, long waitlisted, long seatsLeft,
                                       boolean openForEnrollment, String myStatus, UUID myEnrollmentId) {}

    public record SeatCountResponse(UUID offeringId, long enrolled, long waitlisted) {}

    public record EnrollmentCheckResponse(boolean enrolled, String status) {}

    public record RegistrationResponse(UUID id, UUID studentId, String studentName, String studentNumber,
                                       String targetType, UUID targetId, String targetLabel,
                                       UUID actorId, String actorName, String actorRole, String action,
                                       OffsetDateTime createdAt) {}

    public record MyStudentResponse(UUID studentId, String name, String studentNumber, String email,
                                    String programName, Integer yearOfStudy, List<String> courseCodes,
                                    List<UUID> offeringIds) {}

    public record StudentOption(UUID id, String name, String studentNumber, String email,
                                UUID activeProgramId, String activeProgramName) {}

    public record ProgramCount(UUID degreeProgramId, String degreeProgramName, long activeStudents) {}

    public record OfferingCount(UUID offeringId, long enrolled, long waitlisted) {}

    public record MonthCount(String month, long count) {}

    public record StatsResponse(long activeProgramEnrollments, long activeCourseEnrollments, long waitlisted,
                                List<ProgramCount> byProgram, List<OfferingCount> byOffering,
                                List<MonthCount> enrollmentsByMonth) {}
}
