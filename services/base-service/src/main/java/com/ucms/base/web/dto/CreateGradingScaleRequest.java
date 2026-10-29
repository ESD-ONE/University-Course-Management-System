package com.ucms.base.web.dto;

import com.ucms.base.domain.ReferenceStatus;
import jakarta.validation.constraints.NotBlank;

public record CreateGradingScaleRequest(@NotBlank String name, @NotBlank String bands, ReferenceStatus status) {}
