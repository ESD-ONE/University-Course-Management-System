package com.ucms.base.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "course_categories")
public class CourseCategory extends Auditable {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReferenceStatus status;

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ReferenceStatus getStatus() { return status; }
    public void setStatus(ReferenceStatus status) { this.status = status; }
}
