package com.ucms.identity.web.dto;

import com.ucms.identity.domain.Permission;
import java.util.UUID;

public record PermissionResponse(UUID id, String module, String actionKey) {
    public static PermissionResponse from(Permission p) {
        return new PermissionResponse(p.getId(), p.getModule(), p.getActionKey());
    }
}
