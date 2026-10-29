package com.ucms.base.web.dto;

import com.ucms.base.domain.ReferenceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UpdateDepartmentRequest(@NotBlank String name, @NotBlank String code,
                                       @NotNull UUID facultyId, @NotNull ReferenceStatus status) {}
