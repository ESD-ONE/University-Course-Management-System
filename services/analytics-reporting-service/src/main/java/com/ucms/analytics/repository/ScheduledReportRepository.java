package com.ucms.analytics.repository;

import com.ucms.analytics.domain.ScheduledReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduledReportRepository extends JpaRepository<ScheduledReport, UUID> {
    Optional<ScheduledReport> findByIdAndDeletedAtIsNull(UUID id);

    @Query("select s from ScheduledReport s join fetch s.report where s.deletedAt is null order by s.createdAt asc")
    List<ScheduledReport> findAllLive();

    @Query("select s from ScheduledReport s join fetch s.report where s.deletedAt is null "
            + "and s.status = com.ucms.analytics.domain.ScheduledReport.Status.ACTIVE and s.nextRunAt <= :now")
    List<ScheduledReport> findDue(@Param("now") OffsetDateTime now);
}
