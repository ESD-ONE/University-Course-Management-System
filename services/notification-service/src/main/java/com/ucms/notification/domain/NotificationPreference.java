package com.ucms.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
@EntityListeners(AuditingEntityListener.class)
public class NotificationPreference {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    @Column(name = "assignment_alerts", nullable = false)
    private boolean assignmentAlerts = true;

    @Column(name = "grade_alerts", nullable = false)
    private boolean gradeAlerts = true;

    @Column(name = "registration_alerts", nullable = false)
    private boolean registrationAlerts = true;

    @Version
    @Column(nullable = false)
    private Integer version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public boolean isEmailEnabled() { return emailEnabled; }
    public void setEmailEnabled(boolean emailEnabled) { this.emailEnabled = emailEnabled; }
    public boolean isAssignmentAlerts() { return assignmentAlerts; }
    public void setAssignmentAlerts(boolean assignmentAlerts) { this.assignmentAlerts = assignmentAlerts; }
    public boolean isGradeAlerts() { return gradeAlerts; }
    public void setGradeAlerts(boolean gradeAlerts) { this.gradeAlerts = gradeAlerts; }
    public boolean isRegistrationAlerts() { return registrationAlerts; }
    public void setRegistrationAlerts(boolean registrationAlerts) { this.registrationAlerts = registrationAlerts; }
    public Integer getVersion() { return version; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }

    /** Whether alerts of this category reach the user at all (SYSTEM always does). */
    public boolean allows(Notification.Category category) {
        return switch (category) {
            case REGISTRATION -> registrationAlerts;
            case ASSIGNMENT -> assignmentAlerts;
            case GRADE -> gradeAlerts;
            case SYSTEM -> true;
        };
    }
}
