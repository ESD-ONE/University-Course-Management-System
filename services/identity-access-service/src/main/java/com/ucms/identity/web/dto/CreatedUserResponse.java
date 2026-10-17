package com.ucms.identity.web.dto;

import com.ucms.identity.domain.User;

import java.util.UUID;

/**
 * Like UserResponse, plus the one-time temporary password when the admin didn't supply
 * one — present only on this creation response, never on GET /users or /users/me, so it
 * can't leak into a later read.
 */
public record CreatedUserResponse(UUID id, String name, String email, String role, String status,
                                   String temporaryPassword) {
    public static CreatedUserResponse from(User user, String temporaryPassword) {
        return new CreatedUserResponse(
                user.getId(), user.getName(), user.getEmail(),
                user.getRole().getKind().name(), user.getStatus().name(),
                temporaryPassword);
    }
}
