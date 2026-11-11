package com.ucms.grading.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "lecture_sessions")
public class LectureSession extends Auditable {

    public enum Status { DRAFT, PUBLISHED, CANCELLED }
    public enum DeliveryMode { ONLINE, PHYSICAL, HYBRID }

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "course_offering_id", nullable = false)
    private UUID courseOfferingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "week_id", nullable = false)
    private CourseWeek week;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_mode", nullable = false)
    private DeliveryMode deliveryMode;

    @Column(name = "meeting_url", length = 1000)
    private String meetingUrl;

    private String location;

    @Column(name = "recording_url", length = 1000)
    private String recordingUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    public UUID getId() { return id; }
    public UUID getCourseOfferingId() { return courseOfferingId; }
    public void setCourseOfferingId(UUID courseOfferingId) { this.courseOfferingId = courseOfferingId; }
    public CourseWeek getWeek() { return week; }
    public void setWeek(CourseWeek week) { this.week = week; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public OffsetDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(OffsetDateTime startsAt) { this.startsAt = startsAt; }
    public OffsetDateTime getEndsAt() { return endsAt; }
    public void setEndsAt(OffsetDateTime endsAt) { this.endsAt = endsAt; }
    public DeliveryMode getDeliveryMode() { return deliveryMode; }
    public void setDeliveryMode(DeliveryMode deliveryMode) { this.deliveryMode = deliveryMode; }
    public String getMeetingUrl() { return meetingUrl; }
    public void setMeetingUrl(String meetingUrl) { this.meetingUrl = meetingUrl; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getRecordingUrl() { return recordingUrl; }
    public void setRecordingUrl(String recordingUrl) { this.recordingUrl = recordingUrl; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
}
