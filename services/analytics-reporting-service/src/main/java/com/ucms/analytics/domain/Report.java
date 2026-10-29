package com.ucms.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reports")
public class Report extends AuditableNoSoftDelete {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String format;

    @Column(name = "last_generated_at")
    private OffsetDateTime lastGeneratedAt;

    @Column(name = "object_key")
    private String objectKey;

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public OffsetDateTime getLastGeneratedAt() { return lastGeneratedAt; }
    public void setLastGeneratedAt(OffsetDateTime lastGeneratedAt) { this.lastGeneratedAt = lastGeneratedAt; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
}
