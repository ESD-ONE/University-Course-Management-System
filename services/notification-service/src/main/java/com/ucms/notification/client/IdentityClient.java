package com.ucms.notification.client;

import com.ucms.notification.security.JwtValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class IdentityClient {

    public record User(UUID id, String name, String email, String role, String status) {}

    /** The subset of identity-access-service's ProfileResponse used to seed preferences. */
    public record Profile(boolean notifyEmail, boolean notifyAssignments, boolean notifyGrades, boolean notifyRegistrations) {}

    private static final String SERVICE = "Identity & Access";

    private final RestClient restClient;
    private final JwtValidator jwt;

    public IdentityClient(RestClient.Builder builder, @Value("${ucms.identity-service-uri}") String baseUri, JwtValidator jwt) {
        this.restClient = builder.baseUrl(baseUri).build();
        this.jwt = jwt;
    }

    public User getUser(UUID userId) {
        return Upstream.call(SERVICE, "User not found", () -> restClient.get()
                .uri("/users/{id}", userId)
                .header("Authorization", "Bearer " + jwt.mintServiceToken())
                .retrieve().body(User.class));
    }

    /** The caller's own profile, with their own token. */
    public Profile profile(String userToken) {
        return Upstream.call(SERVICE, null, () -> restClient.get()
                .uri("/users/me")
                .header("Authorization", "Bearer " + userToken)
                .retrieve().body(Profile.class));
    }
}
