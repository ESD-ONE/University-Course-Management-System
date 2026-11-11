package com.ucms.grading.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/** One teaching week of a course offering — the unit lectures, materials and
 *  assignments are organized under. */
@Entity
@Table(name = "course_weeks")
public class CourseWeek extends Auditable {

    public enum Status { DRAFT, PUBLISHED }

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "course_offering_id", nullable = false)
    private UUID courseOfferingId;

    @Column(name = "week_number", nullable = false)
    private int weekNumber;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    public UUID getId() { return id; }
    public UUID getCourseOfferingId() { return courseOfferingId; }
    public void setCourseOfferingId(UUID courseOfferingId) { this.courseOfferingId = courseOfferingId; }
    public int getWeekNumber() { return weekNumber; }
    public void setWeekNumber(int weekNumber) { this.weekNumber = weekNumber; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
}
