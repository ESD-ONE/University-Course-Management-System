package com.ucms.base.client;

import com.ucms.base.security.JwtValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Cross-service dependents check for Department deletion (gap-analysis-report.md G-05).
 * academic-structure-service references departments by plain UUID
 * (degree_programs.department_id, course_catalog.owning_department_id) with no FK, so
 * base-service must synchronously ask it whether a department is still
 * referenced before allowing the soft-delete to proceed.
 *
 * Authenticates as base-service's own Keycloak service account (client_credentials),
 * which holds the "svc:academic" role academic-structure-service requires of callers;
 * academic-structure-service treats a trusted service as a GLOBAL administrator, so the
 * permission-filtered lists it queries return every department's entries.
 *
 * Deliberately fails closed: any exception from either call (connection refused, 5xx,
 * timeout, malformed response, ...) is NOT caught here — it propagates to the caller, so
 * an unreachable academic-structure-service blocks the delete instead of silently being
 * treated as "no dependents".
 */
@Component
public class AcademicStructureClient {

    private final RestClient restClient;
    private final JwtValidator jwt;

    public AcademicStructureClient(RestClient.Builder builder,
                                    @Value("${ucms.academic-service-uri}") String academicServiceUri,
                                    JwtValidator jwt) {
        this.restClient = builder.baseUrl(academicServiceUri).build();
        this.jwt = jwt;
    }

    /**
     * True if academic-structure-service reports any degree program or course-catalog
     * entry still referencing this department. Throws (unchecked) if either call fails
     * — the caller must treat that as "cannot verify, so do not delete", never as "no
     * dependents".
     */
    public boolean hasDependents(UUID departmentId) {
        String serviceToken = jwt.mintServiceToken();

        Object[] degreePrograms = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/degree-programs")
                        .queryParam("departmentId", departmentId)
                        .build())
                .header("Authorization", "Bearer " + serviceToken)
                .retrieve()
                .body(Object[].class);
        if (degreePrograms != null && degreePrograms.length > 0) {
            return true;
        }

        // /course-catalog?departmentId= is being added to academic-structure-service in
        // parallel as part of this same fix (see gap-analysis-report.md G-05) — it is
        // expected to behave like /degree-programs' departmentId filter, matching
        // against owningDepartmentId.
        Object[] courseCatalogEntries = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/course-catalog")
                        .queryParam("departmentId", departmentId)
                        .build())
                .header("Authorization", "Bearer " + serviceToken)
                .retrieve()
                .body(Object[].class);
        return courseCatalogEntries != null && courseCatalogEntries.length > 0;
    }

}
