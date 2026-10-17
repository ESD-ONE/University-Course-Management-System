package com.ucms.academic.web.dto;

import com.ucms.academic.domain.CourseStatus;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** lecturerId: who teaches this course THIS year — null falls back to the course
 * template's own default lecturer. See CourseAcademicYear's Javadoc: the same course
 * can be taught by a different lecturer each year it runs. */
public record CreateCourseAcademicYearRequest(@NotNull UUID academicYearId, UUID semesterId,
                                                Integer capacity, UUID lecturerId, @NotNull CourseStatus status) {}
