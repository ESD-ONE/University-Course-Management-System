package com.ucms.identity.web.dto;

import com.ucms.identity.domain.RoleKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateRoleRequest(@NotBlank String name, @NotNull RoleKind kind) {}
