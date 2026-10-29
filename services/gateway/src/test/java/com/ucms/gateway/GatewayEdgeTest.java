package com.ucms.gateway;

import com.ucms.gateway.security.JwtValidator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The gateway's request path end to end: token backstop → per-user rate limit (real Redis)
 * → load-balanced, health-checked route → circuit-breaker fallback. Tokens are "verified"
 * by a mock — signature checking is JwtValidator's own concern; the subject of the token
 * is the caller id the rate limiter keys on.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayEdgeTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static StubService base;
    static StubService enrollment;
    static int deadPort;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        base = new StubService("base");
        enrollment = new StubService("enrollment");
        try (ServerSocket socket = new ServerSocket(0)) {
            deadPort = socket.getLocalPort();
        }
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("ucms.discovery.services.base-service", base::uri);
        registry.add("ucms.discovery.services.enrollment-service", enrollment::uri);
        registry.add("ucms.discovery.services.identity-access-service", () -> "http://localhost:" + deadPort);
        registry.add("ucms.keycloak.url", () -> "http://keycloak.test/auth");
        registry.add("ucms.revocation.enabled", () -> "false");
    }

    @AfterAll
    static void stopStubs() {
        base.close();
        enrollment.close();
    }

    @Autowired WebTestClient client;
    @Autowired ReactiveStringRedisTemplate redis;
    @MockBean JwtValidator jwtValidator;

    @BeforeEach
    void setUp() {
        when(jwtValidator.verify(anyString())).thenAnswer(inv -> Mono.just(jwt(inv.getArgument(0))));
        redis.execute(c -> c.serverCommands().flushAll()).blockLast();
    }

    /** The token text doubles as the user id, so each test picks its own caller. */
    private static Jwt jwt(String uid) {
        return Jwt.withTokenValue(uid).header("alg", "RS256").claim("uid", uid).subject(uid)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    }

    private static String user() {
        return UUID.randomUUID().toString();
    }

    @Test
    void requestWithoutTokenIsRejectedAndStillGetsARequestId() {
        client.get().uri("/api/base/departments").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("X-Request-Id");
    }

    @Test
    void routesThroughTheLoadBalancerToAHealthyInstanceAndPassesTheRequestIdOn() {
        client.get().uri("/api/base/departments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user())
                .header("X-Request-Id", "abc123def456")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Request-Id", "abc123def456")
                .expectBody()
                .jsonPath("$.service").isEqualTo("base")
                .jsonPath("$.path").isEqualTo("/departments")
                .jsonPath("$.requestId").isEqualTo("abc123def456");
    }

    @Test
    void aServiceWithNoReachableInstanceGetsTheFallbackInsteadOfAHang() {
        client.get().uri("/api/identity/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.message").exists();
    }

    @Test
    void enrollmentWritesAreLimitedPerUserWithRetryAfter() {
        String student = user();
        for (int i = 0; i < 10; i++) {
            client.post().uri("/api/enrollment/enrollments")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + student)
                    .exchange().expectStatus().isOk();
        }
        client.post().uri("/api/enrollment/enrollments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + student)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader().exists(HttpHeaders.RETRY_AFTER)
                .expectHeader().valueEquals("X-RateLimit-Remaining", "0")
                .expectBody()
                .jsonPath("$.code").isEqualTo("RATE_LIMITED")
                .jsonPath("$.message").value(m -> assertThat((String) m).startsWith("Too many requests"));

        // Another student has their own bucket, and the limited student can still read.
        client.post().uri("/api/enrollment/enrollments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user())
                .exchange().expectStatus().isOk();
        client.get().uri("/api/enrollment/enrollments/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + student)
                .exchange().expectStatus().isOk();
    }

    @Test
    void theLiveStreamIsNotRateLimited() {
        String student = user();
        for (int i = 0; i < 45; i++) {
            client.get().uri("/api/notification/notifications/stream")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + student)
                    .exchange()
                    .expectStatus().value(s -> assertThat(s).isNotEqualTo(429));
        }
    }
}
