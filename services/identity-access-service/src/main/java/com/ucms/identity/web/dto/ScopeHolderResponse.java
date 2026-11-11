package com.ucms.identity.web.dto;

import com.ucms.identity.domain.RoleScopeAssignment;

import java.util.UUID;

/** Who holds a role at a given scope — e.g. the Program Head of a degree program. */
public record ScopeHolderResponse(UUID assignmentId, UUID userId, String userName, String userEmail,
                                  UUID roleId, String roleName, String scopeType, UUID scopeId) {
    public static ScopeHolderResponse from(RoleScopeAssignment a) {
        return new ScopeHolderResponse(a.getId(), a.getUser().getId(), a.getUser().getName(), a.getUser().getEmail(),
                a.getRole().getId(), a.getRole().getName(), a.getScopeType().name(), a.getScopeId());
    }
}
