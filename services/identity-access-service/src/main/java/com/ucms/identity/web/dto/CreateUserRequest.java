package com.ucms.identity.web.dto;

import com.ucms.identity.domain.UserStatus;
import com.ucms.identity.domain.RoleKind;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * password is optional, matching the wireframe's "New User" modal (name, email, role,
 * status — no password field): if omitted, the service generates a one-time temporary
 * password and returns it once in CreatedUserResponse. status defaults to ACTIVE if
 * omitted; the wireframe's create modal offers Active/Inactive only — Suspended is an
 * admin action taken later via PATCH /users/{id}/status, not a creation-time choice.
 */
public record CreateUserRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        // Optional (blank → a generated temporary password); Keycloak's policy is 12+.
        @Pattern(regexp = "^$|^.{12,128}$", message = "Password must be 12 to 128 characters") String password,
        @NotNull RoleKind role,
        UserStatus status
) {}
