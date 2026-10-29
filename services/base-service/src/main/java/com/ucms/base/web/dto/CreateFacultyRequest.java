package com.ucms.base.web.dto;

import com.ucms.base.domain.ReferenceStatus;
import jakarta.validation.constraints.NotBlank;

public record CreateFacultyRequest(@NotBlank String name, @NotBlank String code, ReferenceStatus status) {}
