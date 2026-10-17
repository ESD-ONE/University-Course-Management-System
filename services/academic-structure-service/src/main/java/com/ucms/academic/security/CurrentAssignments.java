package com.ucms.academic.security;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/** Reads the current request's scope assignments out of Authentication.details,
 * where JwtAuthFilter put them. See docs/SYSTEM_ARCHITECTURE_REPORT.md section 12.3. */
public final class CurrentAssignments {
    private CurrentAssignments() {}

    @SuppressWarnings("unchecked")
    public static List<AssignmentClaim> get() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getDetails() instanceof List<?> list)) return List.of();
        return (List<AssignmentClaim>) list;
    }
}
