package com.ucms.academic.security;

import java.util.Map;
import java.util.UUID;

/** Mirrors identity-access-service's AssignmentClaim — the JWT's embedded scope
 * assignments. See docs/SYSTEM_ARCHITECTURE_REPORT.md section 12.3. */
public record AssignmentClaim(UUID id, UUID roleId, String scopeType, UUID scopeId, Map<UUID, Boolean> overrides) {}
