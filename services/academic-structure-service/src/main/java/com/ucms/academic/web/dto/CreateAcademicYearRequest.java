package com.ucms.academic.web.dto;

import com.ucms.academic.domain.AcademicYearStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateAcademicYearRequest(@NotBlank String name, LocalDate startDate, LocalDate endDate,
                                         @NotNull AcademicYearStatus status, boolean createDefaultSemesters) {}
