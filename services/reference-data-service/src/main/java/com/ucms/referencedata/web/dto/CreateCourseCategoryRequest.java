package com.ucms.referencedata.web.dto;

import com.ucms.referencedata.domain.ReferenceStatus;
import jakarta.validation.constraints.NotBlank;

public record CreateCourseCategoryRequest(@NotBlank String name, @NotBlank String description, ReferenceStatus status) {}
