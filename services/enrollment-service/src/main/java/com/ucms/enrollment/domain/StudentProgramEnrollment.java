package com.ucms.enrollment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "student_program_enrollments")
public class StudentProgramEnrollment extends Auditable {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "degree_program_id", nullable = false)
    private UUID degreeProgramId;

    @Column(name = "intake_academic_year_id", nullable = false)
    private UUID intakeAcademicYearId;

    @Column(name = "current_year_of_study", nullable = false)
    private int currentYearOfStudy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProgramEnrollmentStatus status;

    public UUID getId() { return id; }
    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }
    public UUID getDegreeProgramId() { return degreeProgramId; }
    public void setDegreeProgramId(UUID degreeProgramId) { this.degreeProgramId = degreeProgramId; }
    public UUID getIntakeAcademicYearId() { return intakeAcademicYearId; }
    public void setIntakeAcademicYearId(UUID intakeAcademicYearId) { this.intakeAcademicYearId = intakeAcademicYearId; }
    public int getCurrentYearOfStudy() { return currentYearOfStudy; }
    public void setCurrentYearOfStudy(int currentYearOfStudy) { this.currentYearOfStudy = currentYearOfStudy; }
    public ProgramEnrollmentStatus getStatus() { return status; }
    public void setStatus(ProgramEnrollmentStatus status) { this.status = status; }
}
