package com.ucms.academic.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "academic_years")
public class AcademicYear extends Auditable {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AcademicYearStatus status;

    @OneToMany(mappedBy = "academicYear", fetch = FetchType.LAZY)
    private List<Semester> semesters = new ArrayList<>();

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public AcademicYearStatus getStatus() { return status; }
    public void setStatus(AcademicYearStatus status) { this.status = status; }
    public List<Semester> getSemesters() { return semesters; }
}
