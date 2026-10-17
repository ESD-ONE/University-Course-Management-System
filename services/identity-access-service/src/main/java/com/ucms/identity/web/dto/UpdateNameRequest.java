package com.ucms.identity.web.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateNameRequest(@NotBlank String name) {}
