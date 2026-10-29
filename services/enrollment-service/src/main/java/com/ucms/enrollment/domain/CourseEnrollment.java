package com.ucms.enrollment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/** A student's place in one course offering (academic-structure-service's
 *  course_academic_years row) — enrolled, or waitlisted while the offering is full. */
@Entity
@Table(name = "course_enrollments")
public class CourseEnrollment extends Auditable {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "course_offering_id", nullable = false)
    private UUID courseOfferingId;

    @Column(name = "enrolled_on", nullable = false)
    private LocalDate enrolledOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CourseEnrollmentStatus status;

    public UUID getId() { return id; }
    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }
    public UUID getCourseOfferingId() { return courseOfferingId; }
    public void setCourseOfferingId(UUID courseOfferingId) { this.courseOfferingId = courseOfferingId; }
    public LocalDate getEnrolledOn() { return enrolledOn; }
    public void setEnrolledOn(LocalDate enrolledOn) { this.enrolledOn = enrolledOn; }
    public CourseEnrollmentStatus getStatus() { return status; }
    public void setStatus(CourseEnrollmentStatus status) { this.status = status; }
}
