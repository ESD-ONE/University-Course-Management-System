package com.ucms.academic.security;

import java.util.UUID;

/** A role's baseline permission grants, as resolved by whatever keeps the local cache
 * fresh (see RoleCatalogClient). A separate interface from the HTTP client itself so
 * PermissionResolver's tests can substitute a trivial test double instead of mocking a
 * concrete class. */
public interface RoleCatalog {
    boolean baselineGrant(UUID roleId, String module, String actionKey);
}
