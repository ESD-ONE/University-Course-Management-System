package com.ucms.analytics.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.ucms.analytics.client.SourceClients;
import com.ucms.analytics.domain.Report;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds each report in the library from live source-service data. Pass/fail and letter
 * buckets use the seeded "Standard Percentage" bands (A ≥ 70, B ≥ 60, C ≥ 50, D ≥ 40,
 * F below) — a course result of 40% or more is a pass.
 */
@Component
public class ReportGenerator {

    public record Generated(byte[] content, String extension, String contentType) {}

    private static final double PASS_MARK = 40.0;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SourceClients sources;

    public ReportGenerator(SourceClients sources) {
        this.sources = sources;
    }

    public Generated generate(Report report) {
        return switch (report.getName()) {
            case "Enrollment Summary" -> csv(enrollmentSummary());
            case "Program Performance" -> pdf(report.getName(), programPerformance());
            case "Course Pass Rate" -> csv(coursePassRate());
            case "Grade Distribution" -> pdf(report.getName(), gradeDistribution());
            case "Registration Activity" -> csv(registrationActivity());
            case "User Activity" -> csv(userActivity());
            default -> throw new IllegalArgumentException("No generator for report \"" + report.getName() + "\"");
        };
    }

    private List<List<String>> enrollmentSummary() {
        SourceClients.EnrollmentStats stats = sources.enrollmentStats();
        Map<UUID, SourceClients.Offering> offerings = offeringsById();
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Section", "Degree Program / Course", "Academic Year", "Semester", "Active / Enrolled", "Waitlisted", "Capacity"));
        for (SourceClients.ProgramCount p : stats.byProgram()) {
            rows.add(List.of("Program", nz(p.degreeProgramName()), "", "", String.valueOf(p.activeStudents()), "", ""));
        }
        for (SourceClients.OfferingCount c : stats.byOffering()) {
            SourceClients.Offering o = offerings.get(c.offeringId());
            if (o == null) continue;
            rows.add(List.of("Course", o.code() + " — " + o.name(), nz(o.academicYearName()), nz(o.semesterName()),
                    String.valueOf(c.enrolled()), String.valueOf(c.waitlisted()), String.valueOf(o.capacity())));
        }
        return rows;
    }

    private List<List<String>> coursePassRate() {
        Map<UUID, SourceClients.Offering> offerings = offeringsById();
        Map<UUID, List<SourceClients.CourseResult>> byOffering = sources.courseResults().stream()
                .collect(Collectors.groupingBy(SourceClients.CourseResult::offeringId));
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Course Code", "Course Name", "Academic Year", "Graded Students", "Passed", "Failed", "Pass Rate %", "Average %"));
        byOffering.forEach((offeringId, results) -> {
            SourceClients.Offering o = offerings.get(offeringId);
            long passed = results.stream().filter(r -> r.percentage() >= PASS_MARK).count();
            double avg = results.stream().mapToDouble(SourceClients.CourseResult::percentage).average().orElse(0);
            rows.add(List.of(o == null ? "?" : o.code(), o == null ? "" : o.name(), o == null ? "" : nz(o.academicYearName()),
                    String.valueOf(results.size()), String.valueOf(passed), String.valueOf(results.size() - passed),
                    pct(passed, results.size()), round(avg)));
        });
        return rows;
    }

    private List<List<String>> programPerformance() {
        Map<UUID, SourceClients.Offering> offerings = offeringsById();
        Map<UUID, String> programNames = sources.programs().stream()
                .collect(Collectors.toMap(SourceClients.Program::id, SourceClients.Program::name, (a, b) -> a));
        Map<UUID, List<SourceClients.CourseResult>> byProgram = new LinkedHashMap<>();
        for (SourceClients.CourseResult r : sources.courseResults()) {
            SourceClients.Offering o = offerings.get(r.offeringId());
            if (o != null) byProgram.computeIfAbsent(o.degreeProgramId(), k -> new ArrayList<>()).add(r);
        }
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Degree Program", "Course Results", "Average %", "Pass Rate %"));
        byProgram.forEach((programId, results) -> {
            long passed = results.stream().filter(r -> r.percentage() >= PASS_MARK).count();
            double avg = results.stream().mapToDouble(SourceClients.CourseResult::percentage).average().orElse(0);
            rows.add(List.of(nz(programNames.get(programId)), String.valueOf(results.size()), round(avg), pct(passed, results.size())));
        });
        return rows;
    }

    private List<List<String>> gradeDistribution() {
        Map<String, Long> buckets = new LinkedHashMap<>();
        for (String letter : List.of("A", "B", "C", "D", "F")) buckets.put(letter, 0L);
        List<SourceClients.CourseResult> results = sources.courseResults();
        for (SourceClients.CourseResult r : results) {
            String letter = r.percentage() >= 70 ? "A" : r.percentage() >= 60 ? "B" : r.percentage() >= 50 ? "C"
                    : r.percentage() >= 40 ? "D" : "F";
            buckets.merge(letter, 1L, Long::sum);
        }
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Grade", "Course Results", "Share %"));
        buckets.forEach((letter, count) -> rows.add(List.of(letter, String.valueOf(count), pct(count, results.size()))));
        return rows;
    }

    private List<List<String>> registrationActivity() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Date", "Student", "Student ID", "Registered Into", "Type", "Action", "Registered By", "Role"));
        for (SourceClients.Registration r : sources.registrations(200)) {
            rows.add(List.of(r.createdAt() == null ? "" : r.createdAt().format(STAMP), nz(r.studentName()), nz(r.studentNumber()),
                    nz(r.targetLabel()), nz(r.targetType()), nz(r.action()), nz(r.actorName()), nz(r.actorRole())));
        }
        return rows;
    }

    private List<List<String>> userActivity() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Name", "Email", "Role", "Status", "Student ID"));
        for (SourceClients.User u : sources.allUsers()) {
            rows.add(List.of(nz(u.name()), nz(u.email()), nz(u.role()), nz(u.status()), nz(u.studentId())));
        }
        return rows;
    }

    private Map<UUID, SourceClients.Offering> offeringsById() {
        return sources.offerings(null).stream()
                .collect(Collectors.toMap(SourceClients.Offering::id, Function.identity(), (a, b) -> a));
    }

    static Generated csv(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        for (List<String> row : rows) {
            sb.append(row.stream().map(ReportGenerator::csvCell).collect(Collectors.joining(","))).append("\r\n");
        }
        // BOM so Excel opens UTF-8 (em dashes, non-Latin names) correctly.
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        return new Generated(withBom, "csv", "text/csv");
    }

    /** Quotes every cell, and neutralizes leading =,+,-,@ so a value can't run as a
     *  spreadsheet formula when the CSV is opened. */
    static String csvCell(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    static Generated pdf(String title, List<List<String>> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
        doc.add(new Paragraph("UCMS — " + title, titleFont));
        doc.add(new Paragraph("Generated " + OffsetDateTime.now().format(STAMP), cellFont));
        doc.add(new Paragraph(" "));
        int cols = rows.isEmpty() ? 1 : rows.get(0).size();
        PdfPTable table = new PdfPTable(cols);
        table.setWidthPercentage(100);
        for (int i = 0; i < rows.size(); i++) {
            for (String cell : rows.get(i)) {
                PdfPCell c = new PdfPCell(new Phrase(cell == null ? "" : cell, i == 0 ? headFont : cellFont));
                c.setPadding(5);
                if (i == 0) c.setHorizontalAlignment(Element.ALIGN_LEFT);
                table.addCell(c);
            }
        }
        if (rows.size() <= 1) {
            PdfPCell empty = new PdfPCell(new Phrase("No data yet.", cellFont));
            empty.setColspan(cols);
            table.addCell(empty);
        }
        doc.add(table);
        doc.close();
        return new Generated(out.toByteArray(), "pdf", "application/pdf");
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String pct(long part, long whole) {
        return whole == 0 ? "0.0" : round(part * 100.0 / whole);
    }

    private static String round(double v) {
        return String.valueOf(Math.round(v * 10.0) / 10.0);
    }
}
