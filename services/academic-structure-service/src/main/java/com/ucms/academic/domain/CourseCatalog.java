package com.ucms.academic.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "course_catalog")
public class CourseCatalog extends Auditable {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "default_credits", nullable = false)
    private int defaultCredits;

    /** ref Reference Data's departments, no FK — the subject's authoring department
     * (may differ from a curriculum-linking program's own department, see
     * docs/SYSTEM_ARCHITECTURE_REPORT.md section 7.3). */
    @Column(name = "owning_department_id", nullable = false)
    private UUID owningDepartmentId;

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getDefaultCredits() { return defaultCredits; }
    public void setDefaultCredits(int defaultCredits) { this.defaultCredits = defaultCredits; }
    public UUID getOwningDepartmentId() { return owningDepartmentId; }
    public void setOwningDepartmentId(UUID owningDepartmentId) { this.owningDepartmentId = owningDepartmentId; }
}
