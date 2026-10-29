package com.ucms.analytics.service;

import com.ucms.analytics.domain.Report;
import com.ucms.analytics.domain.ScheduledReport;
import com.ucms.analytics.messaging.EventPublisher;
import com.ucms.analytics.repository.ScheduledReportRepository;
import com.ucms.analytics.security.Caller;
import com.ucms.analytics.web.dto.Dtos.ScheduleRequest;
import com.ucms.analytics.web.dto.Dtos.ScheduleResponse;
import jakarta.persistence.EntityNotFoundException;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Scheduled reports: due schedules are generated every minute and announced as
 * analytics.report-generated — Notification emails the recipients, so delivery goes
 * through the same channel as every other email in the system.
 */
@Service
public class ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);
    private static final Pattern EMAIL = Pattern.compile("[^@\\s,;]+@[^@\\s,;]+\\.[^@\\s,;]+");

    private final ScheduledReportRepository schedules;
    private final ReportService reports;
    private final EventPublisher events;
    private final ZoneId zone;

    public ScheduleService(ScheduledReportRepository schedules, ReportService reports, EventPublisher events,
                           @Value("${ucms.reports.zone:UTC}") String zone) {
        this.schedules = schedules;
        this.reports = reports;
        this.events = events;
        this.zone = ZoneId.of(zone);
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> list() {
        return schedules.findAllLive().stream().map(ScheduleService::toResponse).toList();
    }

    @Transactional
    public ScheduleResponse create(ScheduleRequest request, Caller caller) {
        Report report = reports.find(request.reportId());
        String recipients = normalizeRecipients(request.recipients());
        String spec = Schedules.spec(request.frequency(), request.time(), request.dayOfWeek(), request.dayOfMonth());
        ScheduledReport s = new ScheduledReport();
        s.setReport(report);
        s.setFrequency(spec);
        s.setRecipients(recipients);
        s.setStatus(ScheduledReport.Status.ACTIVE);
        s.setNextRunAt(Schedules.next(spec, OffsetDateTime.now(), zone));
        s.setCreatedBy(caller.id());
        s.setUpdatedBy(caller.id());
        return toResponse(schedules.save(s));
    }

    /** Pause / resume — the wireframe's ⏸ / ▶ toggle. Resuming recalculates the next run
     *  from now, so a long-paused schedule doesn't fire immediately for a missed slot. */
    @Transactional
    public ScheduleResponse setStatus(UUID id, ScheduledReport.Status status, Caller caller) {
        ScheduledReport s = find(id);
        if (status == ScheduledReport.Status.ACTIVE && s.getStatus() != ScheduledReport.Status.ACTIVE) {
            s.setNextRunAt(Schedules.next(s.getFrequency(), OffsetDateTime.now(), zone));
        }
        s.setStatus(status);
        s.setUpdatedBy(caller.id());
        return toResponse(schedules.save(s));
    }

    @Transactional
    public void delete(UUID id, Caller caller) {
        ScheduledReport s = find(id);
        s.setDeletedAt(OffsetDateTime.now());
        s.setDeletedBy(caller.id());
        s.setUpdatedBy(caller.id());
        schedules.save(s);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @SchedulerLock(name = "scheduled-reports", lockAtLeastFor = "PT20S")
    public void runDueSchedules() {
        for (ScheduledReport s : schedules.findDue(OffsetDateTime.now())) {
            try {
                runOne(s.getId());
            } catch (Exception ex) {
                log.warn("Scheduled report {} failed, will retry next cycle: {}", s.getId(), ex.getMessage());
            }
        }
    }

    @Transactional
    public void runOne(UUID scheduleId) {
        ScheduledReport s = find(scheduleId);
        Report report = reports.generate(s.getReport(), null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scheduleId", s.getId().toString());
        data.put("reportId", report.getId().toString());
        data.put("reportName", report.getName());
        data.put("recipients", s.getRecipients());
        data.put("generatedAt", report.getLastGeneratedAt().toString());
        events.publish("analytics.report-generated", data);
        s.setNextRunAt(Schedules.next(s.getFrequency(), OffsetDateTime.now(), zone));
        schedules.save(s);
    }

    private ScheduledReport find(UUID id) {
        return schedules.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new EntityNotFoundException("Schedule not found"));
    }

    static String normalizeRecipients(String raw) {
        List<String> emails = List.of(raw.trim().split("[,;\\s]+"));
        for (String e : emails) {
            if (!EMAIL.matcher(e).matches()) throw new IllegalArgumentException("\"" + e + "\" isn't a valid email address.");
        }
        return String.join(", ", emails);
    }

    private static ScheduleResponse toResponse(ScheduledReport s) {
        return new ScheduleResponse(s.getId(), s.getReport().getId(), s.getReport().getName(), s.getFrequency(),
                Schedules.label(s.getFrequency()), s.getRecipients(), s.getStatus().name(), s.getNextRunAt());
    }
}
