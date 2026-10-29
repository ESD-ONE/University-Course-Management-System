package com.ucms.notification.web.dto;

import com.ucms.notification.domain.Notification;
import com.ucms.notification.domain.NotificationPreference;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class Dtos {
    private Dtos() {}

    public record NotificationResponse(UUID id, String type, String category, String title, String body, String link,
                                       boolean read, OffsetDateTime createdAt) {
        public static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getCategory().name(), n.getTitle(), n.getBody(),
                    n.getLink(), n.getReadAt() != null, n.getCreatedAt());
        }
    }

    public record NotificationPage(List<NotificationResponse> items, long unreadCount, int page, int size, long total) {}

    public record UnreadCount(long count) {}

    public record PreferencesRequest(@NotNull Boolean emailEnabled, @NotNull Boolean assignmentAlerts,
                                     @NotNull Boolean gradeAlerts, @NotNull Boolean registrationAlerts) {}

    public record PreferencesResponse(boolean emailEnabled, boolean assignmentAlerts, boolean gradeAlerts,
                                      boolean registrationAlerts) {
        public static PreferencesResponse from(NotificationPreference p) {
            return new PreferencesResponse(p.isEmailEnabled(), p.isAssignmentAlerts(), p.isGradeAlerts(), p.isRegistrationAlerts());
        }
    }
}
