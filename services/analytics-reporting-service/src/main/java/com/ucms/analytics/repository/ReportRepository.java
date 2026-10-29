package com.ucms.analytics.repository;

import com.ucms.analytics.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {
    List<Report> findAllByOrderByNameAsc();
}
