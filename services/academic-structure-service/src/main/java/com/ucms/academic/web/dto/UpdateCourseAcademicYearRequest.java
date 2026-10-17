package com.ucms.academic.web.dto;

import com.ucms.academic.domain.CourseStatus;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** lecturerId: null means "use the course template's default lecturer" — a full
 * replace like every other override field here, not a partial merge. */
public record UpdateCourseAcademicYearRequest(UUID semesterId, Integer capacity, UUID lecturerId,
                                               @NotNull CourseStatus status) {}
