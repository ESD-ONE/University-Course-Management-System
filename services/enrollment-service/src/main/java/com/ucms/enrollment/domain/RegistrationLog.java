package com.ucms.enrollment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Append-only: who registered whom into what, and when — the "Recent Registrations"
 *  table. Self-service and on-behalf registrations share one write path; only the actor
 *  differs (flow-03-admin-registration.mmd). */
@Entity
@Table(name = "registration_log")
public class RegistrationLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "actor_role", nullable = false)
    private String actorRole;

    @Column(nullable = false)
    private String action;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected RegistrationLog() {}

    public RegistrationLog(UUID studentId, TargetType targetType, UUID targetId, UUID actorId, String actorRole, String action) {
        this.studentId = studentId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.action = action;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() { return id; }
    public UUID getStudentId() { return studentId; }
    public TargetType getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public UUID getActorId() { return actorId; }
    public String getActorRole() { return actorRole; }
    public String getAction() { return action; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
