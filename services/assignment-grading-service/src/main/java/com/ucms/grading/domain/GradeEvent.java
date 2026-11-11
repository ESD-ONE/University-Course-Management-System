package com.ucms.grading.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Append-only grade history: grades are re-graded in place, so this is the only record
 *  of earlier marks. Rows are never updated or deleted. */
@Entity
@Table(name = "grade_events")
public class GradeEvent {

    public enum Action { GRADED, REGRADED, RELEASED }

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "submission_id", nullable = false, updatable = false)
    private UUID submissionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Action action;

    @Column(precision = 7, scale = 2, updatable = false)
    private BigDecimal marks;

    @Column(columnDefinition = "text", updatable = false)
    private String feedback;

    @Column(name = "object_key", length = 500, updatable = false)
    private String objectKey;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    protected GradeEvent() {}

    public GradeEvent(UUID submissionId, Action action, BigDecimal marks, String feedback, String objectKey, UUID actorId) {
        this.submissionId = submissionId;
        this.action = action;
        this.marks = marks;
        this.feedback = feedback;
        this.objectKey = objectKey;
        this.actorId = actorId;
        this.occurredAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getSubmissionId() { return submissionId; }
    public Action getAction() { return action; }
    public BigDecimal getMarks() { return marks; }
    public String getFeedback() { return feedback; }
    public UUID getActorId() { return actorId; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
}
