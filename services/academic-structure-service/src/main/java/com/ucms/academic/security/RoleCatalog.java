package com.ucms.academic.security;

import java.util.UUID;

/** A role's baseline permission grants, as resolved by whatever keeps the local cache
 * fresh (see RoleCatalogClient). A separate interface from the HTTP client itself so
 * PermissionResolver's tests can substitute a trivial test double instead of mocking a
 * concrete class. */
public interface RoleCatalog {
    boolean baselineGrant(UUID roleId, String module, String actionKey);

    /** Resolves a (module, actionKey) pair to the permission id that identifies it in
     * the global permission catalog — the same id AssignmentClaim.overrides() is keyed
     * by. Needed so PermissionResolver.effectiveGrant() can look up whether a specific
     * assignment overrides the permission currently being checked (G-09). Returns null
     * if the pair isn't a known permission (e.g. the cache hasn't synced yet, or the
     * module/actionKey doesn't exist) — callers must treat null as "no override
     * applies," not as an error. */
    UUID permissionIdOf(String module, String actionKey);
}
