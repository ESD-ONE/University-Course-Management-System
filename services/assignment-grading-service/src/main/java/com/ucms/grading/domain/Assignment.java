package com.ucms.grading.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "assignments")
public class Assignment extends Auditable {

    public enum Status { DRAFT, OPEN, CLOSED }

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "course_offering_id", nullable = false)
    private UUID courseOfferingId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "due_date", nullable = false)
    private OffsetDateTime dueDate;

    @Column(name = "max_marks", nullable = false)
    private int maxMarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "brief_file_name")
    private String briefFileName;

    @Column(name = "brief_object_key")
    private String briefObjectKey;

    public UUID getId() { return id; }
    public UUID getCourseOfferingId() { return courseOfferingId; }
    public void setCourseOfferingId(UUID courseOfferingId) { this.courseOfferingId = courseOfferingId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public OffsetDateTime getDueDate() { return dueDate; }
    public void setDueDate(OffsetDateTime dueDate) { this.dueDate = dueDate; }
    public int getMaxMarks() { return maxMarks; }
    public void setMaxMarks(int maxMarks) { this.maxMarks = maxMarks; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getBriefFileName() { return briefFileName; }
    public void setBriefFileName(String briefFileName) { this.briefFileName = briefFileName; }
    public String getBriefObjectKey() { return briefObjectKey; }
    public void setBriefObjectKey(String briefObjectKey) { this.briefObjectKey = briefObjectKey; }
}
