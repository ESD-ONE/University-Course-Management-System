package com.ucms.identity.web.dto;

import com.ucms.identity.domain.ScopeType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** userId null clears the holder. */
public record SetScopeHolderRequest(@NotNull UUID roleId, @NotNull ScopeType scopeType, @NotNull UUID scopeId,
                                    UUID userId) {}
