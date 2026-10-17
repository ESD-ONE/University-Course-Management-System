package com.ucms.identity.web.dto;

import com.ucms.identity.domain.ScopeType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateAssignmentRequest(@NotNull UUID roleId, @NotNull ScopeType scopeType, UUID scopeId) {}
