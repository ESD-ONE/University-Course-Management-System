package com.ucms.referencedata.web.dto;

import com.ucms.referencedata.domain.ReferenceStatus;
import jakarta.validation.constraints.NotBlank;

public record CreateFacultyRequest(@NotBlank String name, @NotBlank String code, ReferenceStatus status) {}
