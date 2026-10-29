package com.ucms.analytics.repository;

import com.ucms.analytics.domain.DashboardSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DashboardSnapshotRepository extends JpaRepository<DashboardSnapshot, UUID> {
    Optional<DashboardSnapshot> findByRoleAndMetricKey(String role, String metricKey);
    List<DashboardSnapshot> findByRole(String role);
}
