package com.ucms.identity.web.dto;

import com.ucms.identity.domain.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull UserStatus status) {}
