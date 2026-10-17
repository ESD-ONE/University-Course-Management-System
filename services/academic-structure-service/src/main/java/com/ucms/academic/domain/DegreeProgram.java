package com.ucms.academic.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "degree_programs")
public class DegreeProgram extends Auditable {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    /** ref Reference Data's departments, no FK — cross-service, resolved via its API. */
    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "duration_years", nullable = false)
    private int durationYears;

    @Column(name = "total_credits", nullable = false)
    private int totalCredits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProgramStatus status;

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public int getDurationYears() { return durationYears; }
    public void setDurationYears(int durationYears) { this.durationYears = durationYears; }
    public int getTotalCredits() { return totalCredits; }
    public void setTotalCredits(int totalCredits) { this.totalCredits = totalCredits; }
    public ProgramStatus getStatus() { return status; }
    public void setStatus(ProgramStatus status) { this.status = status; }
}
