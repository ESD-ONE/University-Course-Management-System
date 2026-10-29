package com.ucms.analytics.service;

import com.ucms.analytics.domain.Report;
import com.ucms.analytics.repository.ReportRepository;
import com.ucms.analytics.storage.FileStorage;
import com.ucms.analytics.web.dto.Dtos.ReportResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** Generate stores the file in MinIO and stamps last_generated_at; Download streams the
 *  most recently generated file (wireframe: separate Generate / Download buttons). */
@Service
public class ReportService {

    public record Download(String fileName, String contentType, InputStream content) {}

    private final ReportRepository reports;
    private final ReportGenerator generator;
    private final FileStorage storage;

    public ReportService(ReportRepository reports, ReportGenerator generator, FileStorage storage) {
        this.reports = reports;
        this.generator = generator;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> list() {
        return reports.findAllByOrderByNameAsc().stream().map(ReportService::toResponse).toList();
    }

    @Transactional
    public ReportResponse generate(UUID reportId, UUID actorId) {
        return toResponse(generate(find(reportId), actorId));
    }

    @Transactional
    public Report generate(Report report, UUID actorId) {
        ReportGenerator.Generated file = generator.generate(report);
        OffsetDateTime now = OffsetDateTime.now();
        String key = "reports/" + report.getId() + "/" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "." + file.extension();
        storage.put(key, new ByteArrayInputStream(file.content()), file.content().length, file.contentType());
        report.setObjectKey(key);
        report.setLastGeneratedAt(now);
        report.setUpdatedBy(actorId);
        return reports.save(report);
    }

    @Transactional(readOnly = true)
    public Download download(UUID reportId) {
        Report report = find(reportId);
        if (report.getObjectKey() == null) {
            throw new EntityNotFoundException("This report hasn't been generated yet — generate it first.");
        }
        String ext = report.getFormat().toLowerCase();
        String date = report.getLastGeneratedAt().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String fileName = report.getName().replaceAll("[^A-Za-z0-9]+", "-") + "-" + date + "." + ext;
        return new Download(fileName, "PDF".equals(report.getFormat()) ? "application/pdf" : "text/csv",
                storage.get(report.getObjectKey()));
    }

    Report find(UUID id) {
        return reports.findById(id).orElseThrow(() -> new EntityNotFoundException("Report not found"));
    }

    private static ReportResponse toResponse(Report r) {
        return new ReportResponse(r.getId(), r.getName(), r.getDescription(), r.getFormat(), r.getLastGeneratedAt(),
                r.getObjectKey() != null);
    }
}
