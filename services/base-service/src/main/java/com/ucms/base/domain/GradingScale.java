package com.ucms.base.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "grading_scales")
public class GradingScale extends Auditable {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String bands;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReferenceStatus status;

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBands() { return bands; }
    public void setBands(String bands) { this.bands = bands; }
    public ReferenceStatus getStatus() { return status; }
    public void setStatus(ReferenceStatus status) { this.status = status; }
}
