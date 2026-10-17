package com.ucms.identity.web.dto;

import com.ucms.identity.domain.User;

import java.util.UUID;

/**
 * Like UserResponse, plus the account's own notification preferences — only returned by
 * GET /users/me, never GET /users, since preferences are private settings, not something
 * one user should see about another.
 */
public record ProfileResponse(UUID id, String name, String email, String role, String status, String studentId,
                               boolean notifyEmail, boolean notifyAssignments, boolean notifyGrades,
                               boolean notifyRegistrations) {
    public static ProfileResponse from(User user) {
        return new ProfileResponse(
                user.getId(), user.getName(), user.getEmail(),
                user.getRole().getKind().name(), user.getStatus().name(), user.getStudentId(),
                user.isNotifyEmail(), user.isNotifyAssignments(), user.isNotifyGrades(), user.isNotifyRegistrations());
    }
}
