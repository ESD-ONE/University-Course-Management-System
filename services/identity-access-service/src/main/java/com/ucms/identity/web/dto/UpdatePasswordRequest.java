package com.ucms.identity.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 12, max = 128, message = "New password must be 12 to 128 characters") String newPassword
) {}
