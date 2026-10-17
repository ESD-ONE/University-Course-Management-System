package com.ucms.academic.web.dto;

import com.ucms.academic.domain.ProgramStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** code and departmentId aren't editable — code is the program's permanent identity,
 * and changing departmentId would silently move who can manage this program. */
public record UpdateDegreeProgramRequest(@NotBlank String name, @Positive int durationYears,
                                          @Positive int totalCredits, @NotNull ProgramStatus status) {}
