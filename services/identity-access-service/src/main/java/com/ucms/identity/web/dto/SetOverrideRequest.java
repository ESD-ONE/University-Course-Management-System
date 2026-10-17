package com.ucms.identity.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SetOverrideRequest(@NotNull UUID permissionId, @NotNull Boolean granted) {}
