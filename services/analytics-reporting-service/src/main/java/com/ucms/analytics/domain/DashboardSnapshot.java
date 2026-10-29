package com.ucms.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A precomputed dashboard metric, overwritten on each recompute (tables-06). */
@Entity
@Table(name = "dashboard_snapshots")
public class DashboardSnapshot {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String role;

    @Column(name = "metric_key", nullable = false)
    private String metricKey;

    @Column(name = "metric_value", nullable = false, columnDefinition = "text")
    private String metricValue;

    @Column(name = "computed_at", nullable = false)
    private OffsetDateTime computedAt;

    public UUID getId() { return id; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getMetricKey() { return metricKey; }
    public void setMetricKey(String metricKey) { this.metricKey = metricKey; }
    public String getMetricValue() { return metricValue; }
    public void setMetricValue(String metricValue) { this.metricValue = metricValue; }
    public OffsetDateTime getComputedAt() { return computedAt; }
    public void setComputedAt(OffsetDateTime computedAt) { this.computedAt = computedAt; }
}
