package com.ucms.identity.web.dto;

import java.util.UUID;

public record RolePermissionGrant(UUID permissionId, String module, String actionKey, boolean granted) {}
