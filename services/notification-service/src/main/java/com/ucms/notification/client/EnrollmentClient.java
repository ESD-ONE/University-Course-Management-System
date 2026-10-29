package com.ucms.notification.client;

import com.ucms.notification.security.JwtValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

/** Resolves "every student enrolled in this offering" for assignment-published fan-out. */
@Component
public class EnrollmentClient {

    public record RosterEntry(UUID studentId, String status) {}

    private final RestClient restClient;
    private final JwtValidator jwt;

    public EnrollmentClient(RestClient.Builder builder, @Value("${ucms.enrollment-service-uri}") String baseUri, JwtValidator jwt) {
        this.restClient = builder.baseUrl(baseUri).build();
        this.jwt = jwt;
    }

    public List<UUID> enrolledStudentIds(UUID offeringId) {
        List<RosterEntry> roster = Upstream.call("Enrollment", null, () -> restClient.get()
                .uri(b -> b.path("/course-enrollments").queryParam("offeringId", offeringId).build())
                .header("Authorization", "Bearer " + jwt.mintServiceToken())
                .retrieve().body(new ParameterizedTypeReference<List<RosterEntry>>() {}));
        return roster == null ? List.of()
                : roster.stream().filter(r -> "ENROLLED".equals(r.status())).map(RosterEntry::studentId).toList();
    }
}
