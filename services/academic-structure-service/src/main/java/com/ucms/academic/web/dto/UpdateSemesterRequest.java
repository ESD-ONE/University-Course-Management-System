package com.ucms.academic.web.dto;

import com.ucms.academic.domain.SemesterStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record UpdateSemesterRequest(@NotBlank String name, @NotNull Integer sequence,
                                     OffsetDateTime enrollmentOpenAt, OffsetDateTime enrollmentCloseAt,
                                     @NotNull SemesterStatus status) {}
