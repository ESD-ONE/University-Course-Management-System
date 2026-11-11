package com.ucms.notification.client;

import com.ucms.notification.security.JwtValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

/** Resolves "every student enrolled in this offering" for assignment-published fan-out. */
@Component
public class EnrollmentClient {

    public record RosterEntry(UUID studentId, String status) {}

    /** /course-enrollments is paginated (page/size/content/...); only content is needed here. */
    private record RosterPage(List<RosterEntry> content) {}

    private final RestClient restClient;
    private final JwtValidator jwt;

    public EnrollmentClient(RestClient.Builder builder, @Value("${ucms.enrollment-service-uri}") String baseUri, JwtValidator jwt) {
        this.restClient = builder.baseUrl(baseUri).build();
        this.jwt = jwt;
    }

    /** Requests a page large enough to cover a whole course's capacity in one go. */
    public List<UUID> enrolledStudentIds(UUID offeringId) {
        RosterPage page = Upstream.call("Enrollment", null, () -> restClient.get()
                .uri(b -> b.path("/course-enrollments").queryParam("offeringId", offeringId).queryParam("size", 200).build())
                .header("Authorization", "Bearer " + jwt.mintServiceToken())
                .retrieve().body(RosterPage.class));
        return page == null ? List.of()
                : page.content().stream().filter(r -> "ENROLLED".equals(r.status())).map(RosterEntry::studentId).toList();
    }
}
