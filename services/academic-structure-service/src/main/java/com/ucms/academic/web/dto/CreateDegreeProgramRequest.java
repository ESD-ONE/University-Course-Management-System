package com.ucms.academic.web.dto;

import com.ucms.academic.domain.ProgramStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record CreateDegreeProgramRequest(@NotBlank String code, @NotBlank String name,
                                          @NotNull UUID departmentId, @Positive int durationYears,
                                          @Positive int totalCredits, ProgramStatus status) {}
