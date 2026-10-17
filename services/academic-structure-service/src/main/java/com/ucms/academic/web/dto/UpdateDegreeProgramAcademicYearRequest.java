package com.ucms.academic.web.dto;

import com.ucms.academic.domain.DegreeProgramYearStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateDegreeProgramAcademicYearRequest(Integer durationYears, Integer totalCredits,
                                                       @NotNull DegreeProgramYearStatus status) {}
