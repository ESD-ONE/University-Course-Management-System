package com.ucms.enrollment.security;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/** The authenticated account making the current request. */
public record Caller(UUID id, String role, String token) {

    public static Caller current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UUID id)) {
            throw new org.springframework.security.access.AccessDeniedException("Not authenticated");
        }
        String role = auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .findFirst().orElse("");
        return new Caller(id, role, (String) auth.getCredentials());
    }

    public boolean isAdmin() { return "ADMIN".equals(role); }
    public boolean isLecturer() { return "LECTURER".equals(role); }
    public boolean isStudent() { return "STUDENT".equals(role); }
}
