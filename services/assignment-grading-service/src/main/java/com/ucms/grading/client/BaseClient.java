package com.ucms.grading.client;

import com.ucms.grading.security.JwtValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/** microservices-architecture.mmd: "GRADE -.-> REF: resolves" — grading scales. */
@Component
public class BaseClient {

    public record GradingScale(String name, String bands, String status) {}

    private final RestClient restClient;
    private final JwtValidator jwt;

    public BaseClient(RestClient.Builder builder, @Value("${ucms.base-service-uri}") String baseUri, JwtValidator jwt) {
        this.restClient = builder.baseUrl(baseUri).build();
        this.jwt = jwt;
    }

    public List<GradingScale> gradingScales() {
        List<GradingScale> scales = Upstream.call("Base", null, () -> restClient.get()
                .uri("/grading-scales")
                .header("Authorization", "Bearer " + jwt.mintServiceToken())
                .retrieve().body(new ParameterizedTypeReference<List<GradingScale>>() {}));
        return scales == null ? List.of() : scales;
    }
}
