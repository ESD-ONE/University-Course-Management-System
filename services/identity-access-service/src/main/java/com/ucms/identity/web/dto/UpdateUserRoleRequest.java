package com.ucms.identity.web.dto;

import com.ucms.identity.domain.RoleKind;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(@NotNull RoleKind role) {}
