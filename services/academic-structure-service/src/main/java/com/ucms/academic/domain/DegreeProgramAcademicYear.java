package com.ucms.academic.domain;

import jakarta.persistence.*;

/**
 * Assigns a reference degree (DegreeProgram) to a specific academic year — lets a
 * degree be discontinued (or simply not offered) for a given year without deleting its
 * definition or curriculum. duration/credits override the reference degree's defaults
 * when set. See docs/SYSTEM_ARCHITECTURE_REPORT.md-style design: template + per-year
 * assignment, mirrored by Course/CourseAcademicYear.
 */
@Entity
@Table(name = "degree_program_academic_years")
public class DegreeProgramAcademicYear extends Auditable {

    @Id
    @GeneratedValue
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "degree_program_id", nullable = false)
    private DegreeProgram degreeProgram;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @Column(name = "duration_years")
    private Integer durationYears;

    @Column(name = "total_credits")
    private Integer totalCredits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DegreeProgramYearStatus status;

    public java.util.UUID getId() { return id; }
    public DegreeProgram getDegreeProgram() { return degreeProgram; }
    public void setDegreeProgram(DegreeProgram degreeProgram) { this.degreeProgram = degreeProgram; }
    public AcademicYear getAcademicYear() { return academicYear; }
    public void setAcademicYear(AcademicYear academicYear) { this.academicYear = academicYear; }
    public Integer getDurationYears() { return durationYears; }
    public void setDurationYears(Integer durationYears) { this.durationYears = durationYears; }
    public Integer getTotalCredits() { return totalCredits; }
    public void setTotalCredits(Integer totalCredits) { this.totalCredits = totalCredits; }
    public DegreeProgramYearStatus getStatus() { return status; }
    public void setStatus(DegreeProgramYearStatus status) { this.status = status; }
}
