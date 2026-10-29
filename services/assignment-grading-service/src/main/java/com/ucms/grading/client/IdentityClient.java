package com.ucms.grading.client;

import com.ucms.grading.security.JwtValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class IdentityClient {

    public record User(UUID id, String name, String email, String role, String status, String studentId) {}

    private static final String SERVICE = "Identity & Access";
    private static final int LOOKUP_CHUNK = 100;

    private final RestClient restClient;
    private final JwtValidator jwt;

    public IdentityClient(RestClient.Builder builder, @Value("${ucms.identity-service-uri}") String baseUri,
                          JwtValidator jwt) {
        this.restClient = builder.baseUrl(baseUri).build();
        this.jwt = jwt;
    }

    public User getUser(UUID userId) {
        return Upstream.call(SERVICE, "User not found", () -> restClient.get()
                .uri("/users/{id}", userId)
                .header("Authorization", "Bearer " + jwt.mintServiceToken())
                .retrieve().body(User.class));
    }

    /** Missing/deleted ids are simply absent from the map. */
    public Map<UUID, User> lookup(Collection<UUID> ids) {
        Map<UUID, User> result = new HashMap<>();
        List<UUID> all = new ArrayList<>(ids.stream().distinct().toList());
        for (int i = 0; i < all.size(); i += LOOKUP_CHUNK) {
            List<UUID> chunk = all.subList(i, Math.min(i + LOOKUP_CHUNK, all.size()));
            List<User> users = Upstream.call(SERVICE, null, () -> restClient.get()
                    .uri(b -> b.path("/users/lookup").queryParam("ids", chunk.toArray()).build())
                    .header("Authorization", "Bearer " + jwt.mintServiceToken())
                    .retrieve().body(new ParameterizedTypeReference<List<User>>() {}));
            if (users != null) users.forEach(u -> result.put(u.id(), u));
        }
        return result;
    }
}
