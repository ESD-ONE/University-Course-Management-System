package com.ucms.academic.web.dto;

import com.ucms.academic.domain.DegreeProgramYearStatus;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** courseIds: which of the program's courses to also assign to this academic year in
 * the same action — null means "every course currently in this program" (the default,
 * "select all" behavior the Assign Degree to Year dialog starts with); an explicit list
 * (including an empty one) means exactly those, letting a course be dropped for this
 * year without deleting it from the program. */
public record CreateDegreeProgramAcademicYearRequest(@NotNull UUID academicYearId, Integer durationYears,
                                                       Integer totalCredits, @NotNull DegreeProgramYearStatus status,
                                                       List<UUID> courseIds) {}
