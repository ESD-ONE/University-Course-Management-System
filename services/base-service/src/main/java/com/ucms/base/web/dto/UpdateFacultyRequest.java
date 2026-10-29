package com.ucms.base.web.dto;

import com.ucms.base.domain.ReferenceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateFacultyRequest(@NotBlank String name, @NotBlank String code, @NotNull ReferenceStatus status) {}
