package com.ucms.analytics.web;

import com.ucms.analytics.domain.ScheduledReport;
import com.ucms.analytics.security.Caller;
import com.ucms.analytics.service.DashboardService;
import com.ucms.analytics.service.ReportService;
import com.ucms.analytics.service.ScheduleService;
import com.ucms.analytics.service.SnapshotService;
import com.ucms.analytics.web.dto.Dtos.AdminDashboard;
import com.ucms.analytics.web.dto.Dtos.LecturerDashboard;
import com.ucms.analytics.web.dto.Dtos.ReportResponse;
import com.ucms.analytics.web.dto.Dtos.ScheduleRequest;
import com.ucms.analytics.web.dto.Dtos.ScheduleResponse;
import com.ucms.analytics.web.dto.Dtos.ScheduleStatusRequest;
import com.ucms.analytics.web.dto.Dtos.StudentDashboard;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class AnalyticsController {

    private final SnapshotService snapshots;
    private final DashboardService dashboards;
    private final ReportService reports;
    private final ScheduleService schedules;

    public AnalyticsController(SnapshotService snapshots, DashboardService dashboards, ReportService reports,
                               ScheduleService schedules) {
        this.snapshots = snapshots;
        this.dashboards = dashboards;
        this.reports = reports;
        this.schedules = schedules;
    }

    @GetMapping("/dashboards/admin")
    public AdminDashboard admin() {
        return snapshots.adminDashboard();
    }

    /** Forces a recompute now instead of waiting for the next cycle. */
    @PostMapping("/dashboards/admin/refresh")
    public AdminDashboard refresh() {
        snapshots.recompute();
        return snapshots.adminDashboard();
    }

    @GetMapping("/dashboards/lecturer")
    public LecturerDashboard lecturer() {
        return dashboards.lecturer(Caller.current());
    }

    @GetMapping("/dashboards/student")
    public StudentDashboard student() {
        return dashboards.student(Caller.current());
    }

    @GetMapping("/reports")
    public List<ReportResponse> reports() {
        return reports.list();
    }

    @PostMapping("/reports/{id}/generate")
    public ReportResponse generate(@PathVariable("id") UUID id) {
        return reports.generate(id, Caller.current().id());
    }

    @GetMapping("/reports/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable("id") UUID id) {
        ReportService.Download d = reports.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.fileName()).build().toString())
                .body(new InputStreamResource(d.content()));
    }

    @GetMapping("/scheduled-reports")
    public List<ScheduleResponse> schedules() {
        return schedules.list();
    }

    @PostMapping("/scheduled-reports")
    public ResponseEntity<ScheduleResponse> schedule(@Valid @RequestBody ScheduleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(schedules.create(request, Caller.current()));
    }

    @PatchMapping("/scheduled-reports/{id}/status")
    public ScheduleResponse setStatus(@PathVariable("id") UUID id, @Valid @RequestBody ScheduleStatusRequest request) {
        return schedules.setStatus(id, ScheduledReport.Status.valueOf(request.status()), Caller.current());
    }

    @DeleteMapping("/scheduled-reports/{id}")
    public ResponseEntity<Void> deleteSchedule(@PathVariable("id") UUID id) {
        schedules.delete(id, Caller.current());
        return ResponseEntity.noContent().build();
    }
}
