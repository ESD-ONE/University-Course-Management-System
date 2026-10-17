package com.ucms.identity.web.dto;

public record UpdateNotificationPreferencesRequest(
        boolean email,
        boolean assignments,
        boolean grades,
        boolean registrations
) {}
