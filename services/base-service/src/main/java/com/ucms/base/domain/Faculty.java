package com.ucms.base.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "faculties")
public class Faculty extends Auditable {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReferenceStatus status;

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public ReferenceStatus getStatus() { return status; }
    public void setStatus(ReferenceStatus status) { this.status = status; }
}
