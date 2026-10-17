package com.ucms.identity.web.dto;

import com.ucms.identity.domain.Role;
import java.util.List;
import java.util.UUID;

public record RoleResponse(UUID id, String name, String kind, List<RolePermissionGrant> permissions) {
    public static RoleResponse from(Role r, List<RolePermissionGrant> permissions) {
        return new RoleResponse(r.getId(), r.getName(), r.getKind().name(), permissions);
    }
}
