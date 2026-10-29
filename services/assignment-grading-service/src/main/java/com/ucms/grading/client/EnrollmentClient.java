package com.ucms.grading.client;

import com.ucms.grading.security.JwtValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

/** flow-02-grading.mmd: "GRADE->ENR: confirm student is enrolled in this course". */
@Component
public class EnrollmentClient {

    public record Check(boolean enrolled, String status) {}

    public record RosterEntry(UUID id, UUID studentId, String studentName, String studentNumber, String status) {}

    public record MyEnrollment(UUID id, UUID offeringId, String courseCode, String courseName, String status) {}

    private static final String SERVICE = "Enrollment";

    private final RestClient restClient;
    private final JwtValidator jwt;

    public EnrollmentClient(RestClient.Builder builder, @Value("${ucms.enrollment-service-uri}") String baseUri, JwtValidator jwt) {
        this.restClient = builder.baseUrl(baseUri).build();
        this.jwt = jwt;
    }

    public boolean isEnrolled(UUID studentId, UUID offeringId) {
        Check check = Upstream.call(SERVICE, null, () -> restClient.get()
                .uri(b -> b.path("/course-enrollments/check").queryParam("studentId", studentId)
                        .queryParam("offeringId", offeringId).build())
                .header("Authorization", "Bearer " + jwt.mintServiceToken())
                .retrieve().body(Check.class));
        return check != null && check.enrolled();
    }

    /** Enrolled students only (waitlisted students have no seat, so no assignments). */
    public List<RosterEntry> enrolledStudents(UUID offeringId) {
        List<RosterEntry> roster = Upstream.call(SERVICE, null, () -> restClient.get()
                .uri(b -> b.path("/course-enrollments").queryParam("offeringId", offeringId).build())
                .header("Authorization", "Bearer " + jwt.mintServiceToken())
                .retrieve().body(new ParameterizedTypeReference<List<RosterEntry>>() {}));
        return roster == null ? List.of() : roster.stream().filter(r -> "ENROLLED".equals(r.status())).toList();
    }

    public record SeatCount(UUID offeringId, long enrolled, long waitlisted) {}

    public java.util.Map<UUID, Long> enrolledCounts(java.util.Collection<UUID> offeringIds) {
        if (offeringIds.isEmpty()) return java.util.Map.of();
        List<SeatCount> counts = Upstream.call(SERVICE, null, () -> restClient.get()
                .uri(b -> b.path("/offerings/seats").queryParam("ids", offeringIds.toArray()).build())
                .header("Authorization", "Bearer " + jwt.mintServiceToken())
                .retrieve().body(new ParameterizedTypeReference<List<SeatCount>>() {}));
        java.util.Map<UUID, Long> result = new java.util.HashMap<>();
        if (counts != null) counts.forEach(c -> result.put(c.offeringId(), c.enrolled()));
        return result;
    }

    /** The calling student's own enrollments, fetched with their own token. */
    public List<MyEnrollment> myEnrollments(String userToken) {
        List<MyEnrollment> mine = Upstream.call(SERVICE, null, () -> restClient.get()
                .uri("/course-enrollments/me")
                .header("Authorization", "Bearer " + userToken)
                .retrieve().body(new ParameterizedTypeReference<List<MyEnrollment>>() {}));
        return mine == null ? List.of() : mine;
    }
}
