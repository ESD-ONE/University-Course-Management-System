package com.ucms.notification.service;

import com.ucms.notification.client.EnrollmentClient;
import com.ucms.notification.client.IdentityClient;
import com.ucms.notification.domain.Notification;
import com.ucms.notification.domain.Notification.Category;
import com.ucms.notification.domain.NotificationPreference;
import com.ucms.notification.repository.NotificationRepository;
import com.ucms.notification.security.Caller;
import com.ucms.notification.web.dto.Dtos.NotificationPage;
import com.ucms.notification.web.dto.Dtos.NotificationResponse;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * Turns domain events into per-user notifications (flow-01: "NOTIF->NOTIF: send
 * confirmation email + in-app notification"; flow-02: "notify student their grade is
 * ready"). Each delivery: dedupe by (event, user) → check the user's preference for the
 * category → store the in-app notification → push it live → email it if they opted in.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final PreferenceService preferences;
    private final IdentityClient identity;
    private final EnrollmentClient enrollment;
    private final EmailSender email;
    private final LivePushHub push;
    private final String appUrl;

    public NotificationService(NotificationRepository repository, PreferenceService preferences, IdentityClient identity,
                               EnrollmentClient enrollment, EmailSender email, LivePushHub push,
                               @Value("${ucms.app-url:http://localhost:4200}") String appUrl) {
        this.repository = repository;
        this.preferences = preferences;
        this.identity = identity;
        this.enrollment = enrollment;
        this.email = email;
        this.push = push;
        this.appUrl = appUrl;
    }

    @Transactional
    public void handle(UUID eventId, String type, Map<String, Object> data) {
        switch (type) {
            case "enrollment.student-enrolled" -> deliver(uuid(data, "studentId"), eventId, type, Category.REGISTRATION,
                    "Enrolled in " + s(data, "courseCode"),
                    (Boolean.TRUE.equals(data.get("onBehalf")) ? "You've been registered in " : "You're enrolled in ")
                            + s(data, "courseCode") + " — " + s(data, "courseName") + ".",
                    "/app/my-enrollments");
            case "enrollment.student-waitlisted" -> deliver(uuid(data, "studentId"), eventId, type, Category.REGISTRATION,
                    "Waitlisted for " + s(data, "courseCode"),
                    s(data, "courseCode") + " is full — you're on the waitlist and will be enrolled automatically if a seat opens.",
                    "/app/my-enrollments");
            case "enrollment.waitlist-promoted" -> deliver(uuid(data, "studentId"), eventId, type, Category.REGISTRATION,
                    "A seat opened in " + s(data, "courseCode"),
                    "You've been moved off the waitlist and are now enrolled in " + s(data, "courseCode") + " — "
                            + s(data, "courseName") + ".",
                    "/app/my-enrollments");
            case "enrollment.course-dropped" -> deliver(uuid(data, "studentId"), eventId, type, Category.REGISTRATION,
                    "Dropped " + s(data, "courseCode"),
                    "You're no longer enrolled in " + s(data, "courseCode") + " — " + s(data, "courseName") + ".",
                    "/app/my-enrollments");
            case "enrollment.program-registered" -> deliver(uuid(data, "studentId"), eventId, type, Category.REGISTRATION,
                    "Registered in " + s(data, "programName"),
                    "You've been registered in " + s(data, "programName") + " (" + s(data, "programCode") + ").",
                    "/app/my-program");
            case "grading.assignment-published" -> {
                UUID offeringId = uuid(data, "offeringId");
                for (UUID studentId : enrollment.enrolledStudentIds(offeringId)) {
                    deliver(studentId, eventId, type, Category.ASSIGNMENT, "New assignment: " + s(data, "title"),
                            s(data, "courseCode") + " · due " + s(data, "dueDate"), "/app/my-assignments");
                }
            }
            case "grading.submission-received" -> {
                UUID lecturerId = uuid(data, "lecturerId");
                if (lecturerId != null) {
                    deliver(lecturerId, eventId, type, Category.ASSIGNMENT, "New submission: " + s(data, "title"),
                            "A student submitted work for " + s(data, "courseCode") + " · " + s(data, "title") + ".",
                            "/app/grading");
                }
            }
            case "grading.grade-posted" -> deliver(uuid(data, "studentId"), eventId, type, Category.GRADE,
                    "Grade posted: " + s(data, "title"),
                    s(data, "courseCode") + " — you scored " + s(data, "marks") + " / " + s(data, "maxMarks") + ".",
                    "/app/grades");
            case "identity.user-created" -> deliver(uuid(data, "userId"), eventId, type, Category.SYSTEM,
                    "Welcome to UCMS", "Hi " + s(data, "name") + ", your account is ready.", "/app/dashboard");
            case "analytics.report-generated" -> emailReport(data);
            default -> log.debug("Ignoring event type {}", type);
        }
    }

    private void deliver(UUID userId, UUID eventId, String type, Category category, String title, String body, String link) {
        if (userId == null) return;
        if (eventId != null && repository.existsBySourceEventIdAndUserId(eventId, userId)) return;
        NotificationPreference prefs = preferences.forUser(userId);
        if (!prefs.allows(category)) return;

        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setCategory(category);
        n.setTitle(title);
        n.setBody(body);
        n.setLink(link);
        n.setSourceEventId(eventId);
        NotificationResponse saved = NotificationResponse.from(repository.save(n));
        boolean sendEmail = prefs.isEmailEnabled();

        // Only after the row is committed: otherwise a browser could be pushed (or emailed)
        // a notification that then rolls back, and the SMTP round-trip would hold the
        // database transaction open.
        afterCommit(() -> {
            push.push(userId, saved);
            if (sendEmail) {
                try {
                    IdentityClient.User user = identity.getUser(userId);
                    email.send(user.email(), "[UCMS] " + title, body + "\n\n" + appUrl + link);
                } catch (Exception ex) {
                    log.warn("Skipped email for {}: {}", userId, ex.getMessage());
                }
            }
        });
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /** Scheduled reports go to free-form recipient addresses, not users — email only. */
    private void emailReport(Map<String, Object> data) {
        String recipients = s(data, "recipients");
        if (recipients.isBlank()) return;
        String subject = "[UCMS] Scheduled report: " + s(data, "reportName");
        String body = "Your scheduled report \"" + s(data, "reportName") + "\" was generated at " + s(data, "generatedAt")
                + ".\n\nDownload it from the Reports screen: " + appUrl + "/app/reports";
        Arrays.stream(recipients.split("[,;\\s]+")).filter(r -> !r.isBlank()).forEach(r -> email.send(r.trim(), subject, body));
    }

    @Transactional(readOnly = true)
    public NotificationPage mine(Caller caller, boolean unreadOnly, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<Notification> result = unreadOnly
                ? repository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(caller.id(), pageable)
                : repository.findByUserIdOrderByCreatedAtDesc(caller.id(), pageable);
        return new NotificationPage(result.map(NotificationResponse::from).getContent(),
                repository.countByUserIdAndReadAtIsNull(caller.id()), result.getNumber(), result.getSize(),
                result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public long unreadCount(Caller caller) {
        return repository.countByUserIdAndReadAtIsNull(caller.id());
    }

    @Transactional
    public NotificationResponse markRead(UUID id, Caller caller) {
        Notification n = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Notification not found"));
        if (!n.getUserId().equals(caller.id())) throw new AccessDeniedException("Not your notification");
        if (n.getReadAt() == null) {
            n.setReadAt(OffsetDateTime.now());
            n = repository.save(n);
        }
        return NotificationResponse.from(n);
    }

    @Transactional
    public int markAllRead(Caller caller) {
        return repository.markAllRead(caller.id(), OffsetDateTime.now());
    }

    private static String s(Map<String, Object> data, String key) {
        Object v = data.get(key);
        return v == null ? "" : v.toString();
    }

    private static UUID uuid(Map<String, Object> data, String key) {
        Object v = data.get(key);
        if (v == null) return null;
        try {
            return UUID.fromString(v.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
