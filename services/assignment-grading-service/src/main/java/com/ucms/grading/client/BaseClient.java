package com.ucms.grading.client;

import com.ucms.grading.security.JwtValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/** microservices-architecture.mmd: "GRADE -.-> REF: resolves" — grading scales. */
@Component
public class BaseClient {

    public record GradingScale(String name, String bands, String status) {}

    /** /grading-scales is paginated (page/size/content/...); only content is needed here. */
    private record GradingScalePage(List<GradingScale> content) {}

    private final RestClient restClient;
    private final JwtValidator jwt;

    public BaseClient(RestClient.Builder builder, @Value("${ucms.base-service-uri}") String baseUri, JwtValidator jwt) {
        this.restClient = builder.baseUrl(baseUri).build();
        this.jwt = jwt;
    }

    /** Every grading scale — a small, admin-maintained catalog, so this requests
     * /grading-scales' own max page size (100) rather than its default of 20. */
    public List<GradingScale> gradingScales() {
        GradingScalePage page = Upstream.call("Base", null, () -> restClient.get()
                .uri(b -> b.path("/grading-scales").queryParam("size", 100).build())
                .header("Authorization", "Bearer " + jwt.mintServiceToken())
                .retrieve().body(GradingScalePage.class));
        return page == null ? List.of() : page.content();
    }
}
