package com.ucms.base.domain;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Completes docs/architecture/tables-07-reference-data.mmd's FACULTIES ||--o{ DEPARTMENTS,
 * which was drawn but never implemented — "Department of Mathematics" previously existed
 * only as a faked row in the faculties table (code DOM). See
 * docs/SYSTEM_ARCHITECTURE_REPORT.md section 2.3.
 */
@Entity
@Table(name = "departments")
public class Department extends Auditable {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReferenceStatus status;

    public UUID getId() { return id; }
    public Faculty getFaculty() { return faculty; }
    public void setFaculty(Faculty faculty) { this.faculty = faculty; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public ReferenceStatus getStatus() { return status; }
    public void setStatus(ReferenceStatus status) { this.status = status; }
}
