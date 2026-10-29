package com.ucms.base.web.dto;

import com.ucms.base.domain.ReferenceStatus;
import jakarta.validation.constraints.NotBlank;

public record CreateCourseCategoryRequest(@NotBlank String name, @NotBlank String description, ReferenceStatus status) {}
