package com.ucms.academic.web.dto;

import com.ucms.academic.domain.CourseStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;

/** No academicYearId/semesterId — a course is a reusable template now, assigned to
 * specific academic years separately via CreateCourseAcademicYearRequest. lecturerId is
 * optional: omit it for a lecturer's own self-service creation (defaults to the
 * caller); an admin or department head may set it explicitly to create a course on
 * another lecturer's behalf — CourseOfferingService enforces who may do which. */
public record CreateCourseRequest(@NotNull UUID catalogCourseId, @NotNull UUID degreeProgramId,
                                   @NotNull LocalDate beginDate, @Positive int yearOfStudy,
                                   @Positive int capacity, @NotNull CourseStatus status, UUID lecturerId) {}
