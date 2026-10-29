package com.ucms.analytics.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucms.analytics.client.SourceClients;
import com.ucms.analytics.domain.DashboardSnapshot;
import com.ucms.analytics.repository.DashboardSnapshotRepository;
import com.ucms.analytics.web.dto.Dtos.AdminDashboard;
import com.ucms.analytics.web.dto.Dtos.AdminKpis;
import com.ucms.analytics.web.dto.Dtos.Point;
import com.ucms.analytics.web.dto.Dtos.RecentRegistration;
import com.ucms.analytics.web.dto.Dtos.TopCourse;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Precomputes the admin dashboard and each course's class average into
 * dashboard_snapshots (flow-02: "ANALYTICS->ANALYTICS: recompute class average, update
 * dashboard snapshot"). Recomputed every 5 minutes, and within ~15 seconds of any
 * enrollment/grading/identity event (the event only marks the snapshot dirty, so a burst
 * of events costs one recompute, not one per event). A source being down keeps the last
 * good snapshot rather than blanking the dashboard.
 */
@Service
public class SnapshotService {

    public static final String ADMIN = "ADMIN";
    public static final String LECTURER = "LECTURER";
    private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);

    private final SourceClients sources;
    private final DashboardSnapshotRepository repository;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean dirty = new AtomicBoolean(true);

    public SnapshotService(SourceClients sources, DashboardSnapshotRepository repository, ObjectMapper objectMapper) {
        this.sources = sources;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void markDirty() {
        dirty.set(true);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    @SchedulerLock(name = "dashboard-snapshot", lockAtMostFor = "PT5M")
    public void scheduledRecompute() {
        tryRecompute();
    }

    @Scheduled(fixedDelay = 15_000)
    @SchedulerLock(name = "dashboard-snapshot", lockAtMostFor = "PT5M")
    public void recomputeIfDirty() {
        if (dirty.get()) tryRecompute();
    }

    private void tryRecompute() {
        try {
            recompute();
        } catch (Exception ex) {
            log.warn("Dashboard snapshot recompute failed, keeping the previous one: {}", ex.getMessage());
        }
    }

    @Transactional
    public synchronized void recompute() {
        dirty.set(false);
        SourceClients.UserSummary users = sources.userSummary();
        List<SourceClients.Offering> offerings = sources.offerings(null);
        List<SourceClients.Program> programs = sources.programs();
        SourceClients.EnrollmentStats stats = sources.enrollmentStats();

        long activeCourses = offerings.stream()
                .filter(o -> "PUBLISHED".equals(o.status()) && !"ARCHIVED".equals(o.academicYearStatus())).count();
        long activePrograms = programs.stream().filter(p -> "ACTIVE".equals(p.status())).count();
        save(ADMIN, "kpis", new AdminKpis(users.students(), activeCourses, activePrograms,
                stats.activeCourseEnrollments(), stats.waitlisted()));

        List<Point> trend = new ArrayList<>();
        long running = 0;
        for (SourceClients.MonthCount m : stats.enrollmentsByMonth()) {
            running += m.count();
            trend.add(new Point(m.month(), running));
        }
        save(ADMIN, "enrollment_trend", trend);

        save(ADMIN, "students_by_program", stats.byProgram().stream()
                .map(p -> new Point(p.degreeProgramName() == null ? "Unknown program" : p.degreeProgramName(), p.activeStudents()))
                .toList());

        Map<UUID, SourceClients.Offering> byId = offerings.stream()
                .collect(Collectors.toMap(SourceClients.Offering::id, Function.identity(), (a, b) -> a));
        List<SourceClients.OfferingCount> top = stats.byOffering().stream()
                .filter(c -> byId.containsKey(c.offeringId()))
                .sorted(Comparator.comparingLong(SourceClients.OfferingCount::enrolled).reversed())
                .limit(5).toList();
        Map<UUID, SourceClients.User> lecturers = sources.lookupUsers(top.stream()
                .map(c -> byId.get(c.offeringId()).lecturerId()).filter(java.util.Objects::nonNull).toList());
        save(ADMIN, "top_courses", top.stream().map(c -> {
            SourceClients.Offering o = byId.get(c.offeringId());
            SourceClients.User l = lecturers.get(o.lecturerId());
            return new TopCourse(o.id(), o.code(), o.name(), l == null ? null : l.name(), c.enrolled());
        }).toList());

        save(ADMIN, "recent_registrations", sources.registrations(5).stream()
                .map(r -> new RecentRegistration(r.studentName(), r.targetLabel(), r.actorName(), r.actorRole(),
                        r.action(), r.createdAt()))
                .toList());

        for (SourceClients.OfferingSummary s : sources.offeringSummaries(byId.keySet())) {
            if (s.classAverage() != null) save(LECTURER, "class_average:" + s.offeringId(), s.classAverage());
        }
    }

    @Transactional
    public AdminDashboard adminDashboard() {
        if (repository.findByRoleAndMetricKey(ADMIN, "kpis").isEmpty()) recompute();
        Map<String, DashboardSnapshot> snaps = repository.findByRole(ADMIN).stream()
                .collect(Collectors.toMap(DashboardSnapshot::getMetricKey, Function.identity()));
        OffsetDateTime computedAt = snaps.values().stream().map(DashboardSnapshot::getComputedAt)
                .min(Comparator.naturalOrder()).orElse(null);
        return new AdminDashboard(
                read(snaps.get("kpis"), new TypeReference<AdminKpis>() {}),
                read(snaps.get("enrollment_trend"), new TypeReference<List<Point>>() {}),
                read(snaps.get("students_by_program"), new TypeReference<List<Point>>() {}),
                read(snaps.get("top_courses"), new TypeReference<List<TopCourse>>() {}),
                read(snaps.get("recent_registrations"), new TypeReference<List<RecentRegistration>>() {}),
                computedAt);
    }

    @Transactional(readOnly = true)
    public Double classAverage(UUID offeringId) {
        return repository.findByRoleAndMetricKey(LECTURER, "class_average:" + offeringId)
                .map(s -> Double.valueOf(s.getMetricValue())).orElse(null);
    }

    private void save(String role, String key, Object value) {
        DashboardSnapshot s = repository.findByRoleAndMetricKey(role, key).orElseGet(() -> {
            DashboardSnapshot fresh = new DashboardSnapshot();
            fresh.setRole(role);
            fresh.setMetricKey(key);
            return fresh;
        });
        try {
            s.setMetricValue(objectMapper.writeValueAsString(value));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        s.setComputedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repository.save(s);
    }

    private <T> T read(DashboardSnapshot s, TypeReference<T> type) {
        if (s == null) return null;
        try {
            return objectMapper.readValue(s.getMetricValue(), type);
        } catch (Exception e) {
            return null;
        }
    }
}
