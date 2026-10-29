package com.ucms.identity.web.dto;

import com.ucms.identity.domain.User;

import java.util.List;
import java.util.UUID;

/**
 * Like UserResponse, plus the account's own notification preferences — only returned by
 * GET /users/me, never GET /users, since preferences are private settings, not something
 * one user should see about another.
 *
 * {@code assignments} (Gap G-01): the frontend has no concept of DEPARTMENT-scoped
 * authority otherwise — without this, a "Department Head"-shaped account (a LECTURER
 * with an extra DEPARTMENT-scoped role_scope_assignment) is indistinguishable from a
 * plain Lecturer in the UI, despite academic-structure-service's PermissionResolver
 * already granting them real department-wide authority server-side. Override detail is
 * deliberately omitted here (always an empty list) — GET /users/{id}/assignments is the
 * place to see a specific assignment's overrides; this field exists only so the frontend
 * can tell "does this account hold any DEPARTMENT-scoped assignment" for nav/guard
 * purposes, not to replicate full permission resolution client-side.
 */
public record ProfileResponse(UUID id, String name, String email, String role, String status, String studentId,
                               boolean notifyEmail, boolean notifyAssignments, boolean notifyGrades,
                               boolean notifyRegistrations, List<AssignmentResponse> assignments) {
    public static ProfileResponse from(User user, List<AssignmentResponse> assignments) {
        return new ProfileResponse(
                user.getId(), user.getName(), user.getEmail(),
                user.getRole().getKind().name(), user.getStatus().name(), user.getStudentId(),
                user.isNotifyEmail(), user.isNotifyAssignments(), user.isNotifyGrades(), user.isNotifyRegistrations(),
                assignments);
    }
}
