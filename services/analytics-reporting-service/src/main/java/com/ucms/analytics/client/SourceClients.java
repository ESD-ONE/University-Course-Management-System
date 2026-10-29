package com.ucms.analytics.client;

import com.ucms.analytics.security.JwtValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only views of the four services Analytics reports on (microservices-architecture.mmd:
 * "ANALYTICS reads IAM / ACAD / ENR / GRADE"). Aggregate reads use a service token;
 * per-user dashboards forward the caller's own token so each source service applies its
 * own "is this yours" rules.
 */
@Component
public class SourceClients {

    // Identity & Access
    public record UserSummary(long total, long active, long students, long staff) {}
    public record User(UUID id, String name, String email, String role, String status, String studentId) {}
    public record UserPage(List<User> content, int totalPages) {}

    // Academic Structure
    public record Offering(UUID id, String code, String name, int credits, UUID degreeProgramId, String degreeProgramName,
                           String academicYearName, String academicYearStatus, String semesterName, int capacity,
                           UUID lecturerId, String status) {}
    public record Program(UUID id, String code, String name, int totalCredits, String status) {}

    // Enrollment
    public record ProgramCount(UUID degreeProgramId, String degreeProgramName, long activeStudents) {}
    public record OfferingCount(UUID offeringId, long enrolled, long waitlisted) {}
    public record MonthCount(String month, long count) {}
    public record EnrollmentStats(long activeProgramEnrollments, long activeCourseEnrollments, long waitlisted,
                                  List<ProgramCount> byProgram, List<OfferingCount> byOffering,
                                  List<MonthCount> enrollmentsByMonth) {}
    public record Registration(UUID id, String studentName, String studentNumber, String targetType, String targetLabel,
                               String actorName, String actorRole, String action, OffsetDateTime createdAt) {}
    public record MyEnrollment(UUID offeringId, String courseCode, String courseName, Integer credits,
                               String requirementType, String lecturerName, String academicYearName,
                               String semesterName, String status) {}
    public record MyProgram(UUID degreeProgramId, String degreeProgramCode, String degreeProgramName,
                            Integer totalCredits, Integer durationYears, int currentYearOfStudy,
                            String intakeAcademicYearName, String status) {}

    // Assignment & Grading
    public record CourseResult(UUID offeringId, UUID studentId, double percentage, int gradedAssignments) {}
    public record OfferingSummary(UUID offeringId, String courseCode, String courseName, long totalStudents,
                                  int assignments, long submitted, long graded, long pending, Double classAverage) {}
    public record CourseGrade(UUID offeringId, String courseCode, String courseName, int credits, String academicYearName,
                              String academicYearStatus, String semesterName, int gradedAssignments, Double percentage,
                              String letter, Double points) {}
    public record GradeHistory(List<CourseGrade> courses, Double gpa, int creditsGraded) {}
    public record StudentAssignment(UUID id, String courseCode, String title, OffsetDateTime dueDate, String status,
                                    String mySubmissionStatus) {}

    private final RestClient identity;
    private final RestClient academic;
    private final RestClient enrollment;
    private final RestClient grading;
    private final JwtValidator jwt;

    public SourceClients(RestClient.Builder builder, JwtValidator jwt,
                         @Value("${ucms.identity-service-uri}") String identityUri,
                         @Value("${ucms.academic-service-uri}") String academicUri,
                         @Value("${ucms.enrollment-service-uri}") String enrollmentUri,
                         @Value("${ucms.grading-service-uri}") String gradingUri) {
        this.identity = builder.clone().baseUrl(identityUri).build();
        this.academic = builder.clone().baseUrl(academicUri).build();
        this.enrollment = builder.clone().baseUrl(enrollmentUri).build();
        this.grading = builder.clone().baseUrl(gradingUri).build();
        this.jwt = jwt;
    }

    private String service() {
        return "Bearer " + jwt.mintServiceToken();
    }

    public UserSummary userSummary() {
        return Upstream.call("Identity & Access", null, () -> identity.get().uri("/users/summary")
                .header("Authorization", service()).retrieve().body(UserSummary.class));
    }

    public List<User> allUsers() {
        List<User> all = new ArrayList<>();
        int page = 0, totalPages;
        do {
            int p = page;
            UserPage result = Upstream.call("Identity & Access", null, () -> identity.get()
                    .uri(b -> b.path("/users").queryParam("page", p).queryParam("size", 100).build())
                    .header("Authorization", service()).retrieve().body(UserPage.class));
            if (result == null) break;
            all.addAll(result.content());
            totalPages = result.totalPages();
            page++;
        } while (page < totalPages);
        return all;
    }

    public Map<UUID, User> lookupUsers(Collection<UUID> ids) {
        Map<UUID, User> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        List<User> users = Upstream.call("Identity & Access", null, () -> identity.get()
                .uri(b -> b.path("/users/lookup").queryParam("ids", ids.stream().distinct().toArray()).build())
                .header("Authorization", service()).retrieve().body(new ParameterizedTypeReference<List<User>>() {}));
        if (users != null) users.forEach(u -> result.put(u.id(), u));
        return result;
    }

    public List<Offering> offerings(UUID lecturerId) {
        List<Offering> offerings = Upstream.call("Academic Structure", null, () -> academic.get()
                .uri(b -> {
                    b.path("/course-offerings");
                    if (lecturerId != null) b.queryParam("lecturerId", lecturerId);
                    return b.build();
                })
                .header("Authorization", service()).retrieve().body(new ParameterizedTypeReference<List<Offering>>() {}));
        return offerings == null ? List.of() : offerings;
    }

    public List<Program> programs() {
        List<Program> programs = Upstream.call("Academic Structure", null, () -> academic.get().uri("/degree-programs")
                .header("Authorization", service()).retrieve().body(new ParameterizedTypeReference<List<Program>>() {}));
        return programs == null ? List.of() : programs;
    }

    public EnrollmentStats enrollmentStats() {
        return Upstream.call("Enrollment", null, () -> enrollment.get().uri("/stats")
                .header("Authorization", service()).retrieve().body(EnrollmentStats.class));
    }

    public List<Registration> registrations(int limit) {
        List<Registration> rows = Upstream.call("Enrollment", null, () -> enrollment.get()
                .uri(b -> b.path("/registrations").queryParam("limit", limit).build())
                .header("Authorization", service()).retrieve().body(new ParameterizedTypeReference<List<Registration>>() {}));
        return rows == null ? List.of() : rows;
    }

    public List<MyEnrollment> myEnrollments(String userToken) {
        List<MyEnrollment> rows = Upstream.call("Enrollment", null, () -> enrollment.get().uri("/course-enrollments/me")
                .header("Authorization", "Bearer " + userToken).retrieve()
                .body(new ParameterizedTypeReference<List<MyEnrollment>>() {}));
        return rows == null ? List.of() : rows;
    }

    public List<MyProgram> myPrograms(String userToken) {
        List<MyProgram> rows = Upstream.call("Enrollment", null, () -> enrollment.get().uri("/program-enrollments/me")
                .header("Authorization", "Bearer " + userToken).retrieve()
                .body(new ParameterizedTypeReference<List<MyProgram>>() {}));
        return rows == null ? List.of() : rows;
    }

    public List<CourseResult> courseResults() {
        List<CourseResult> rows = Upstream.call("Assignment & Grading", null, () -> grading.get().uri("/stats/course-results")
                .header("Authorization", service()).retrieve().body(new ParameterizedTypeReference<List<CourseResult>>() {}));
        return rows == null ? List.of() : rows;
    }

    public List<OfferingSummary> offeringSummaries(Collection<UUID> offeringIds) {
        if (offeringIds.isEmpty()) return List.of();
        List<OfferingSummary> rows = Upstream.call("Assignment & Grading", null, () -> grading.get()
                .uri(b -> b.path("/stats/offering-summaries").queryParam("ids", offeringIds.toArray()).build())
                .header("Authorization", service()).retrieve()
                .body(new ParameterizedTypeReference<List<OfferingSummary>>() {}));
        return rows == null ? List.of() : rows;
    }

    public GradeHistory gradeHistory(String userToken) {
        return Upstream.call("Assignment & Grading", null, () -> grading.get().uri("/grades/me")
                .header("Authorization", "Bearer " + userToken).retrieve().body(GradeHistory.class));
    }

    public List<StudentAssignment> myAssignments(String userToken) {
        List<StudentAssignment> rows = Upstream.call("Assignment & Grading", null, () -> grading.get().uri("/assignments")
                .header("Authorization", "Bearer " + userToken).retrieve()
                .body(new ParameterizedTypeReference<List<StudentAssignment>>() {}));
        return rows == null ? List.of() : rows;
    }
}
