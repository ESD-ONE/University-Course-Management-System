package com.ucms.academic.domain;

import jakarta.persistence.*;
import java.util.UUID;

/** Assigns a course template to a specific academic year + semester — "the same course
 * can be assigned to many years" without re-creating it. capacity/status override the
 * course template's defaults when set. */
@Entity
@Table(name = "course_academic_years")
public class CourseAcademicYear extends Auditable {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id")
    private Semester semester;

    @Column
    private Integer capacity;

    /** ref Identity and Access's users, no FK. Overrides Course.lecturerId for this
     * year only — the same course can be taught by a different lecturer each year. */
    @Column(name = "lecturer_id")
    private UUID lecturerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CourseStatus status;

    public UUID getId() { return id; }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public AcademicYear getAcademicYear() { return academicYear; }
    public void setAcademicYear(AcademicYear academicYear) { this.academicYear = academicYear; }
    public Semester getSemester() { return semester; }
    public void setSemester(Semester semester) { this.semester = semester; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public UUID getLecturerId() { return lecturerId; }
    public void setLecturerId(UUID lecturerId) { this.lecturerId = lecturerId; }
    public CourseStatus getStatus() { return status; }
    public void setStatus(CourseStatus status) { this.status = status; }
}
