package com.ucms.grading.client;

import com.ucms.grading.security.JwtValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
public class AcademicClient {

    public record Offering(UUID id, String code, String name, int credits, UUID degreeProgramId,
                           UUID academicYearId, String academicYearName, String academicYearStatus,
                           String semesterName, int capacity, UUID lecturerId, String status) {}

    public record Access(boolean staffView, boolean manage) {}

    private static final String SERVICE = "Academic Structure";

    private final RestClient restClient;
    private final JwtValidator jwt;

    public AcademicClient(RestClient.Builder builder, @Value("${ucms.academic-service-uri}") String baseUri, JwtValidator jwt) {
        this.restClient = builder.baseUrl(baseUri).build();
        this.jwt = jwt;
    }

    /** Whether the caller may view this offering's delivery as staff, and whether they may
     * manage it (owning lecturer, admin, or a scoped Program/Department Head) — see
     * CourseOfferingQueryService#access in academic-structure-service. */
    public Access access(UUID offeringId, String callerToken) {
        return Upstream.call(SERVICE, "Course offering not found", () -> restClient.get()
                .uri("/course-offerings/{id}/access", offeringId)
                .header("Authorization", "Bearer " + callerToken)
                .retrieve().body(Access.class));
    }

    public Offering getOffering(UUID offeringId) {
        return Upstream.call(SERVICE, "Course offering not found", () -> restClient.get()
                .uri("/course-offerings/{id}", offeringId)
                .header("Authorization", "Bearer " + jwt.mintServiceToken())
                .retrieve().body(Offering.class));
    }

    /** lecturerId and ids are both optional filters. */
    public List<Offering> searchOfferings(UUID lecturerId, Collection<UUID> ids) {
        return Upstream.call(SERVICE, null, () -> restClient.get()
                .uri(b -> {
                    b.path("/course-offerings");
                    if (lecturerId != null) b.queryParam("lecturerId", lecturerId);
                    if (ids != null && !ids.isEmpty()) b.queryParam("ids", ids.toArray());
                    return b.build();
                })
                .header("Authorization", "Bearer " + jwt.mintServiceToken())
                .retrieve().body(new ParameterizedTypeReference<List<Offering>>() {}));
    }
}
