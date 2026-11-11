package com.ucms.enrollment.client;

import com.ucms.enrollment.security.JwtValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Academic Structure lookups, authenticated with a service token (flow-01/03: "ENR->ACAD:
 *  GET course (open? seats left?)" / "GET degree program (exists? active?)"). */
@Component
public class AcademicClient {

    public record Offering(UUID id, UUID courseId, UUID catalogCourseId, String code, String name, int credits,
                           String requirementType, UUID degreeProgramId, String degreeProgramCode,
                           String degreeProgramName, UUID departmentId, int yearOfStudy, UUID academicYearId,
                           String academicYearName, String academicYearStatus, UUID semesterId, String semesterName,
                           OffsetDateTime enrollmentOpenAt, OffsetDateTime enrollmentCloseAt, int capacity,
                           UUID lecturerId, String status, boolean openForEnrollment) {}

    public record Program(UUID id, String code, String name, UUID departmentId, int durationYears,
                          int totalCredits, String status) {}

    public record AcademicYear(UUID id, String name, String status) {}

    /** /degree-programs is paginated (page/size/content/...); only content is needed here. */
    private record ProgramPage(List<Program> content) {}

    private static final String SERVICE = "Academic Structure";

    private final RestClient restClient;
    private final JwtValidator jwt;

    public AcademicClient(RestClient.Builder builder, @Value("${ucms.academic-service-uri}") String baseUri,
                          JwtValidator jwt) {
        this.restClient = builder.baseUrl(baseUri).build();
        this.jwt = jwt;
    }

    public Offering getOffering(UUID offeringId) {
        return Upstream.call(SERVICE, "Course offering not found", () -> restClient.get()
                .uri("/course-offerings/{id}", offeringId)
                .header("Authorization", "Bearer " + jwt.mintServiceToken())
                .retrieve().body(Offering.class));
    }

    public List<Offering> searchOfferings(String status, UUID academicYearId, UUID lecturerId, Collection<UUID> ids) {
        return Upstream.call(SERVICE, null, () -> restClient.get()
                .uri(b -> {
                    b.path("/course-offerings");
                    if (status != null) b.queryParam("status", status);
                    if (academicYearId != null) b.queryParam("academicYearId", academicYearId);
                    if (lecturerId != null) b.queryParam("lecturerId", lecturerId);
                    if (ids != null && !ids.isEmpty()) b.queryParam("ids", ids.toArray());
                    return b.build();
                })
                .header("Authorization", "Bearer " + jwt.mintServiceToken())
                .retrieve().body(new ParameterizedTypeReference<List<Offering>>() {}));
    }

    public Program getProgram(UUID programId) {
        return Upstream.call(SERVICE, "Degree program not found", () -> restClient.get()
                .uri("/degree-programs/{id}", programId)
                .header("Authorization", "Bearer " + jwt.mintServiceToken())
                .retrieve().body(Program.class));
    }

    /** Every program — callers build an id→name/details lookup from this, so this
     * requests /degree-programs' own max page size (100) rather than its default of 20.
     * Degree programs are a small, slow-growing reference catalog; this silently drops
     * anything past the 100th if that assumption is ever outgrown. */
    public List<Program> listPrograms() {
        ProgramPage page = Upstream.call(SERVICE, null, () -> restClient.get()
                .uri(b -> b.path("/degree-programs").queryParam("size", 100).build())
                .header("Authorization", "Bearer " + jwt.mintServiceToken())
                .retrieve().body(ProgramPage.class));
        return page == null ? List.of() : page.content();
    }

    public List<AcademicYear> listAcademicYears() {
        return Upstream.call(SERVICE, null, () -> restClient.get()
                .uri("/academic-years")
                .header("Authorization", "Bearer " + jwt.mintServiceToken())
                .retrieve().body(new ParameterizedTypeReference<List<AcademicYear>>() {}));
    }
}
