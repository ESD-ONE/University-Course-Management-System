package com.ucms.notification.web;

import com.ucms.notification.security.Caller;
import com.ucms.notification.service.LivePushHub;
import com.ucms.notification.service.NotificationService;
import com.ucms.notification.service.PreferenceService;
import com.ucms.notification.web.dto.Dtos.NotificationPage;
import com.ucms.notification.web.dto.Dtos.NotificationResponse;
import com.ucms.notification.web.dto.Dtos.PreferencesRequest;
import com.ucms.notification.web.dto.Dtos.PreferencesResponse;
import com.ucms.notification.web.dto.Dtos.UnreadCount;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

@RestController
public class NotificationController {

    private final NotificationService notifications;
    private final PreferenceService preferences;
    private final LivePushHub push;

    public NotificationController(NotificationService notifications, PreferenceService preferences, LivePushHub push) {
        this.notifications = notifications;
        this.preferences = preferences;
        this.push = push;
    }

    @GetMapping("/notifications/me")
    public NotificationPage mine(@RequestParam(name = "unreadOnly", defaultValue = "false") boolean unreadOnly,
                                 @RequestParam(name = "page", defaultValue = "0") int page,
                                 @RequestParam(name = "size", defaultValue = "20") int size) {
        return notifications.mine(Caller.current(), unreadOnly, page, size);
    }

    @GetMapping("/notifications/me/unread-count")
    public UnreadCount unreadCount() {
        return new UnreadCount(notifications.unreadCount(Caller.current()));
    }

    @PatchMapping("/notifications/{id}/read")
    public NotificationResponse markRead(@PathVariable("id") UUID id) {
        return notifications.markRead(id, Caller.current());
    }

    @PostMapping("/notifications/me/read-all")
    public Map<String, Integer> markAllRead() {
        return Map.of("updated", notifications.markAllRead(Caller.current()));
    }

    /** Live push stream (Server-Sent Events) of the caller's new notifications. */
    @GetMapping(path = "/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return push.connect(Caller.current().id());
    }

    @GetMapping("/preferences/me")
    public PreferencesResponse myPreferences() {
        return preferences.mine(Caller.current());
    }

    @PutMapping("/preferences/me")
    public PreferencesResponse updatePreferences(@Valid @RequestBody PreferencesRequest request) {
        return preferences.update(request, Caller.current());
    }
}
