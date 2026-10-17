package com.ucms.academic.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "semesters")
public class Semester extends Auditable {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int sequence;

    @Column(name = "enrollment_open_at")
    private OffsetDateTime enrollmentOpenAt;

    @Column(name = "enrollment_close_at")
    private OffsetDateTime enrollmentCloseAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SemesterStatus status;

    public UUID getId() { return id; }
    public AcademicYear getAcademicYear() { return academicYear; }
    public void setAcademicYear(AcademicYear academicYear) { this.academicYear = academicYear; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }
    public OffsetDateTime getEnrollmentOpenAt() { return enrollmentOpenAt; }
    public void setEnrollmentOpenAt(OffsetDateTime enrollmentOpenAt) { this.enrollmentOpenAt = enrollmentOpenAt; }
    public OffsetDateTime getEnrollmentCloseAt() { return enrollmentCloseAt; }
    public void setEnrollmentCloseAt(OffsetDateTime enrollmentCloseAt) { this.enrollmentCloseAt = enrollmentCloseAt; }
    public SemesterStatus getStatus() { return status; }
    public void setStatus(SemesterStatus status) { this.status = status; }
}
