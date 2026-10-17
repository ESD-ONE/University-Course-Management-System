package com.ucms.academic.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A reusable course TEMPLATE — a catalog subject taught within a degree program,
 * created once and then assigned to one or more academic years via
 * CourseAcademicYear (so the same course doesn't need to be re-created every year).
 * capacity/status here are the template's defaults; a given year's assignment can
 * override either. See docs/SYSTEM_ARCHITECTURE_REPORT.md-style design note on
 * DegreeProgramAcademicYear for the parallel pattern on the degree-program side.
 */
@Entity
@Table(name = "courses")
public class Course extends Auditable {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_course_id", nullable = false)
    private CourseCatalog catalogCourse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "degree_program_id", nullable = false)
    private DegreeProgram degreeProgram;

    @Column(name = "begin_date", nullable = false)
    private LocalDate beginDate;

    @Column(name = "year_of_study", nullable = false)
    private int yearOfStudy;

    /** ref Identity and Access's users, no FK — cross-service, resolved via its API. */
    @Column(name = "lecturer_id", nullable = false)
    private UUID lecturerId;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CourseStatus status;

    public UUID getId() { return id; }
    public CourseCatalog getCatalogCourse() { return catalogCourse; }
    public void setCatalogCourse(CourseCatalog catalogCourse) { this.catalogCourse = catalogCourse; }
    public DegreeProgram getDegreeProgram() { return degreeProgram; }
    public void setDegreeProgram(DegreeProgram degreeProgram) { this.degreeProgram = degreeProgram; }
    public LocalDate getBeginDate() { return beginDate; }
    public void setBeginDate(LocalDate beginDate) { this.beginDate = beginDate; }
    public int getYearOfStudy() { return yearOfStudy; }
    public void setYearOfStudy(int yearOfStudy) { this.yearOfStudy = yearOfStudy; }
    public UUID getLecturerId() { return lecturerId; }
    public void setLecturerId(UUID lecturerId) { this.lecturerId = lecturerId; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public CourseStatus getStatus() { return status; }
    public void setStatus(CourseStatus status) { this.status = status; }
}
