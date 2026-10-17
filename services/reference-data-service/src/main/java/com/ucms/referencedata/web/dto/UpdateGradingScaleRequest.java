package com.ucms.referencedata.web.dto;

import com.ucms.referencedata.domain.ReferenceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateGradingScaleRequest(@NotBlank String name, @NotBlank String bands, @NotNull ReferenceStatus status) {}
