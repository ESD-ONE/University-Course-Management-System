package com.ucms.academic.web.dto;

import com.ucms.academic.domain.CourseStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;

/** catalogCourseId/degreeProgramId aren't editable after creation — they define what
 * the course IS. lecturerId is optional: omit to leave the current lecturer unchanged;
 * set it to reassign — CourseOfferingService enforces who may do that, same as create. */
public record UpdateCourseRequest(@NotNull LocalDate beginDate, @Positive int yearOfStudy,
                                   @Positive int capacity, @NotNull CourseStatus status, UUID lecturerId) {}
