package com.ucms.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Gap G-18 backstop: every backend service independently validates its own JWT and
 * enforces its own role/scope authorization — this filter does not replace or duplicate
 * that. It exists so a request with no token, a malformed token, an expired token, or a
 * token revoked at logout never reaches a backend service at all.
 *
 * Tokens are Keycloak's (see JwtValidator); signing in happens at Keycloak itself, so no
 * route here is public apart from health checks.
 *
 * Revocation (G-11): identity-access-service writes "ucms:revoked:" + Base64(SHA-256(token))
 * to Redis on logout. If Redis is unreachable the check fails open (logged) — a Redis
 * outage must not take the whole site down; the token is still bounded by its expiry,
 * which Keycloak keeps short (five minutes).
 */
@Component
public class JwtBackstopFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtBackstopFilter.class);
    private static final String ACTUATOR_PREFIX = "/actuator/";
    private static final String REVOKED_PREFIX = "ucms:revoked:";
    /** The verified caller's UCMS user id (the token's uid claim, else its subject), for
     *  filters further down the chain such as the per-user rate limiter. */
    public static final String CALLER_ATTRIBUTE = "ucms.caller";
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 1;

    private final JwtValidator jwtValidator;
    private final ReactiveStringRedisTemplate redis;
    private final boolean revocationEnabled;

    public JwtBackstopFilter(JwtValidator jwtValidator, ReactiveStringRedisTemplate redis,
                             @Value("${ucms.revocation.enabled:true}") boolean revocationEnabled) {
        this.jwtValidator = jwtValidator;
        this.redis = redis;
        this.revocationEnabled = revocationEnabled;
    }

    @Override
    public int getOrder() {
        // Runs ahead of the routing/StripPrefix filters, against the original,
        // unmodified incoming request path (only the request-id filter is earlier).
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (request.getMethod() == HttpMethod.OPTIONS || isPublic(request.getPath().value())) {
            return chain.filter(exchange);
        }

        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return reject(exchange, "Missing bearer token");
        }

        String token = header.substring(7);
        // Only verification failures are mapped to 401 — not errors raised further down
        // the chain once the request has been forwarded.
        return jwtValidator.verify(token)
                .map(jwt -> {
                    String uid = jwt.getClaimAsString("uid");
                    exchange.getAttributes().put(CALLER_ATTRIBUTE, uid != null ? uid : jwt.getSubject());
                    return true;
                })
                .onErrorResume(ex -> Mono.just(false))
                .flatMap(valid -> valid
                        ? forwardUnlessRevoked(exchange, chain, token)
                        : reject(exchange, "Invalid or expired token"));
    }

    private Mono<Void> forwardUnlessRevoked(ServerWebExchange exchange, GatewayFilterChain chain, String token) {
        if (!revocationEnabled) {
            return chain.filter(exchange);
        }
        return redis.hasKey(REVOKED_PREFIX + hash(token))
                .onErrorResume(ex -> {
                    log.warn("Revocation check skipped, Redis unavailable: {}", ex.getMessage());
                    return Mono.just(false);
                })
                .flatMap(revoked -> revoked
                        ? reject(exchange, "Token has been revoked — please sign in again")
                        : chain.filter(exchange));
    }

    private boolean isPublic(String path) {
        return path.startsWith(ACTUATOR_PREFIX);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes());
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
        byte[] body = ("{\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
