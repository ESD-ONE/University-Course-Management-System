package com.ucms.identity.web.dto;

import java.util.Map;
import java.util.UUID;

/**
 * One scope assignment as academic-structure-service's PermissionResolver consumes it,
 * served by GET /internal/assignments/{userId}. (Before Keycloak this was embedded in the
 * JWT at login.) overrides is keyed by permissionId.
 */
public record AssignmentClaim(UUID id, UUID roleId, String scopeType, UUID scopeId, Map<UUID, Boolean> overrides) {}
