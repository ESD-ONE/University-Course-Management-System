package com.ucms.identity.web.dto;

import com.ucms.identity.domain.RoleScopeAssignment;
import java.util.List;
import java.util.UUID;

public record AssignmentResponse(UUID id, UUID userId, UUID roleId, String roleName, String scopeType,
                                  UUID scopeId, List<RolePermissionGrant> overrides) {
    public static AssignmentResponse from(RoleScopeAssignment a, List<RolePermissionGrant> overrides) {
        return new AssignmentResponse(a.getId(), a.getUser().getId(), a.getRole().getId(), a.getRole().getName(),
                a.getScopeType().name(), a.getScopeId(), overrides);
    }
}
