package com.ucms.academic.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "program_courses")
public class ProgramCourse extends AuditableNoSoftDelete {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "degree_program_id", nullable = false)
    private DegreeProgram degreeProgram;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_course_id", nullable = false)
    private CourseCatalog catalogCourse;

    @Enumerated(EnumType.STRING)
    @Column(name = "requirement_type", nullable = false)
    private RequirementType requirementType;

    @Column(name = "recommended_year", nullable = false)
    private int recommendedYear;

    @Column(name = "recommended_semester", nullable = false)
    private int recommendedSemester;

    @Column(name = "credit_value", nullable = false)
    private int creditValue;

    public UUID getId() { return id; }
    public DegreeProgram getDegreeProgram() { return degreeProgram; }
    public void setDegreeProgram(DegreeProgram degreeProgram) { this.degreeProgram = degreeProgram; }
    public CourseCatalog getCatalogCourse() { return catalogCourse; }
    public void setCatalogCourse(CourseCatalog catalogCourse) { this.catalogCourse = catalogCourse; }
    public RequirementType getRequirementType() { return requirementType; }
    public void setRequirementType(RequirementType requirementType) { this.requirementType = requirementType; }
    public int getRecommendedYear() { return recommendedYear; }
    public void setRecommendedYear(int recommendedYear) { this.recommendedYear = recommendedYear; }
    public int getRecommendedSemester() { return recommendedSemester; }
    public void setRecommendedSemester(int recommendedSemester) { this.recommendedSemester = recommendedSemester; }
    public int getCreditValue() { return creditValue; }
    public void setCreditValue(int creditValue) { this.creditValue = creditValue; }
}
