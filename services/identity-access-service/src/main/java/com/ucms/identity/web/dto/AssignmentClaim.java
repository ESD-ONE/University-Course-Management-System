package com.ucms.identity.web.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Embedded in the JWT at login — docs/SYSTEM_ARCHITECTURE_REPORT.md section 12.3's
 * "Phase 1" authz strategy: scope assignments travel with the token so every downstream
 * service can resolve permissions locally, with no synchronous call back to Identity &
 * Access per request. overrides is keyed by permissionId.
 */
public record AssignmentClaim(UUID id, UUID roleId, String scopeType, UUID scopeId, Map<UUID, Boolean> overrides) {}
