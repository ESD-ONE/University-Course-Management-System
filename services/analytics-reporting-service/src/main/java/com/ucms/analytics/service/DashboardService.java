package com.ucms.analytics.service;

import com.ucms.analytics.client.SourceClients;
import com.ucms.analytics.security.Caller;
import com.ucms.analytics.web.dto.Dtos.LecturerCourse;
import com.ucms.analytics.web.dto.Dtos.LecturerDashboard;
import com.ucms.analytics.web.dto.Dtos.Point;
import com.ucms.analytics.web.dto.Dtos.StudentCourse;
import com.ucms.analytics.web.dto.Dtos.StudentDashboard;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Per-user dashboards, computed live from the source services (they're about one
 *  person, so there's nothing worth snapshotting). */
@Service
public class DashboardService {

    private final SourceClients sources;
    private final SnapshotService snapshots;

    public DashboardService(SourceClients sources, SnapshotService snapshots) {
        this.sources = sources;
        this.snapshots = snapshots;
    }

    public LecturerDashboard lecturer(Caller caller) {
        List<SourceClients.Offering> mine = sources.offerings(caller.id()).stream()
                .filter(o -> !"ARCHIVED".equals(o.academicYearStatus())).toList();
        Map<UUID, SourceClients.OfferingSummary> summaries = sources.offeringSummaries(
                        mine.stream().map(SourceClients.Offering::id).toList()).stream()
                .collect(Collectors.toMap(SourceClients.OfferingSummary::offeringId, Function.identity()));

        long totalStudents = 0, graded = 0, pending = 0, notSubmitted = 0;
        double avgSum = 0;
        int avgCount = 0;
        List<LecturerCourse> courses = new ArrayList<>();
        for (SourceClients.Offering o : mine) {
            SourceClients.OfferingSummary s = summaries.get(o.id());
            long enrolled = s == null ? 0 : s.totalStudents();
            int assignments = s == null ? 0 : s.assignments();
            long sub = s == null ? 0 : s.submitted();
            long gr = s == null ? 0 : s.graded();
            Double avg = s != null && s.classAverage() != null ? s.classAverage() : snapshots.classAverage(o.id());
            totalStudents += enrolled;
            graded += gr;
            pending += sub - gr;
            notSubmitted += Math.max(0, enrolled * assignments - sub);
            if (avg != null) {
                avgSum += avg;
                avgCount++;
            }
            courses.add(new LecturerCourse(o.id(), o.code(), o.name(), o.academicYearName(), o.semesterName(),
                    enrolled, o.capacity(), o.status(), assignments, gr, sub, avg));
        }
        Double average = avgCount == 0 ? null : Math.round(avgSum / avgCount * 10.0) / 10.0;
        return new LecturerDashboard(mine.size(), totalStudents, pending, average, graded, pending, notSubmitted, courses);
    }

    public StudentDashboard student(Caller caller) {
        SourceClients.MyProgram program = sources.myPrograms(caller.token()).stream()
                .filter(p -> "ACTIVE".equals(p.status())).findFirst().orElse(null);
        List<SourceClients.MyEnrollment> enrollments = sources.myEnrollments(caller.token()).stream()
                .filter(e -> "ENROLLED".equals(e.status())).toList();
        SourceClients.GradeHistory history = sources.gradeHistory(caller.token());
        List<SourceClients.CourseGrade> graded = history == null ? List.of() : history.courses();
        Map<UUID, SourceClients.CourseGrade> gradeByOffering = graded.stream()
                .collect(Collectors.toMap(SourceClients.CourseGrade::offeringId, Function.identity(), (a, b) -> a));

        // A course counts as completed once its academic year is archived with a passing grade.
        int creditsCompleted = graded.stream()
                .filter(g -> "ARCHIVED".equals(g.academicYearStatus()) && g.points() != null && g.points() > 0)
                .mapToInt(SourceClients.CourseGrade::credits).sum();

        OffsetDateTime now = OffsetDateTime.now();
        int upcoming = (int) sources.myAssignments(caller.token()).stream()
                .filter(a -> "OPEN".equals(a.status()) && "NOT_SUBMITTED".equals(a.mySubmissionStatus()))
                .filter(a -> a.dueDate() != null && a.dueDate().isAfter(now) && a.dueDate().isBefore(now.plusDays(7)))
                .count();

        Map<String, double[]> bySemester = new LinkedHashMap<>();
        for (SourceClients.CourseGrade g : graded) {
            if (g.points() == null || g.credits() == 0) continue;
            String label = (g.semesterName() == null ? "" : g.semesterName() + " · ") + g.academicYearName();
            double[] acc = bySemester.computeIfAbsent(label, k -> new double[2]);
            acc[0] += g.points() * g.credits();
            acc[1] += g.credits();
        }
        List<Point> gpaTrend = bySemester.entrySet().stream()
                .map(e -> new Point(e.getKey(), Math.round(e.getValue()[0] / e.getValue()[1] * 100.0) / 100.0)).toList();

        List<StudentCourse> courses = enrollments.stream().map(e -> {
            SourceClients.CourseGrade g = gradeByOffering.get(e.offeringId());
            return new StudentCourse(e.offeringId(), e.courseCode(), e.courseName(), e.lecturerName(), e.requirementType(),
                    e.credits(), g == null ? null : g.percentage(), g == null ? null : g.letter());
        }).toList();

        return new StudentDashboard(program == null ? null : program.degreeProgramName(),
                program == null ? null : program.degreeProgramCode(),
                program == null ? null : program.currentYearOfStudy(),
                program == null ? null : program.intakeAcademicYearName(),
                enrollments.size(), creditsCompleted, program == null ? null : program.totalCredits(),
                history == null ? null : history.gpa(), upcoming, gpaTrend, courses);
    }
}
