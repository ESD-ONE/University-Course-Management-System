package com.ucms.academic.security;

import org.springframework.security.core.context.SecurityContextHolder;

/** Reads the current request's coarse account-kind role (ADMIN/LECTURER/STUDENT) out of
 * its granted authorities, for the few checks that genuinely need "is this an admin
 * account" rather than a scope/department permission — e.g. creating a course on
 * another lecturer's behalf. */
public final class CurrentRole {
    private CurrentRole() {}

    public static boolean is(String role) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
}
