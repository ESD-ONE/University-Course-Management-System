package com.ucms.base.web.dto;

import com.ucms.base.domain.ReferenceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateGradingScaleRequest(@NotBlank String name, @NotBlank String bands, @NotNull ReferenceStatus status) {}
