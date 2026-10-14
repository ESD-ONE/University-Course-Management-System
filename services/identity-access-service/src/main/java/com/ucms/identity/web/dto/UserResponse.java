package com.ucms.identity.web.dto;

import com.ucms.identity.domain.User;

import java.util.UUID;

public record UserResponse(UUID id, String name, String email, String role, String status) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(), user.getName(), user.getEmail(),
                user.getRole().getKind().name(), user.getStatus().name());
    }
}
