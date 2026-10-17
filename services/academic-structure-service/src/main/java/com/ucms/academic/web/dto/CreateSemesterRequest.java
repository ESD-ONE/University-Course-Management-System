package com.ucms.academic.web.dto;

import com.ucms.academic.domain.SemesterStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record CreateSemesterRequest(@NotBlank String name, @NotNull Integer sequence,
                                     OffsetDateTime enrollmentOpenAt, OffsetDateTime enrollmentCloseAt,
                                     SemesterStatus status) {}
