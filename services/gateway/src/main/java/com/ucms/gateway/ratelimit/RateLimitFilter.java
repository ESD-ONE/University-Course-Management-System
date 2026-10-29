package com.ucms.gateway.ratelimit;

import com.ucms.gateway.security.JwtBackstopFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-caller rate limits, shared by every gateway replica through a token bucket in
 * Redis. The caller is the verified user (set by {@link JwtBackstopFilter}, which runs
 * first), else the client address nginx resolved. Over the limit → 429 with Retry-After.
 *
 * If Redis is unreachable the request is let through (logged), the same stance as the
 * revocation check: a cache outage must not take the site down, and nginx's per-IP
 * limit still applies in front.
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String KEY_PREFIX = "ucms:rl:";
    private static final RedisScript<List> SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/token_bucket.lua"), List.class);

    private record CompiledTier(RateLimitProperties.Tier tier, List<HttpMethod> methods, List<PathPattern> paths) {
        boolean matches(HttpMethod method, PathContainer path) {
            return (methods.isEmpty() || methods.contains(method)) && paths.stream().anyMatch(p -> p.matches(path));
        }
    }

    private record Decision(boolean allowed, long remaining, long retryAfterSeconds) {}

    /** Redis couldn't be asked: let the request through. */
    private static final Decision UNCHECKED = new Decision(true, -1, 0);

    private final RateLimitProperties properties;
    private final ReactiveStringRedisTemplate redis;
    private final List<CompiledTier> tiers = new ArrayList<>();

    public RateLimitFilter(RateLimitProperties properties, ReactiveStringRedisTemplate redis) {
        this.properties = properties;
        this.redis = redis;
        PathPatternParser parser = PathPatternParser.defaultInstance;
        for (RateLimitProperties.Tier tier : properties.getTiers()) {
            tiers.add(new CompiledTier(tier,
                    tier.getMethods().stream().map(HttpMethod::valueOf).toList(),
                    tier.getPaths().stream().map(parser::parse).toList()));
        }
    }

    @Override
    public int getOrder() {
        return JwtBackstopFilter.ORDER + 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!properties.isEnabled() || request.getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }
        RateLimitProperties.Tier tier = tierFor(request);
        if (tier == null || tier.isExempt()) {
            return chain.filter(exchange);
        }

        String key = KEY_PREFIX + tier.getName() + ":" + callerKey(exchange);
        List<String> args = List.of(String.valueOf(tier.tokensPerSecond()), String.valueOf(tier.getBurst()), "1");
        return redis.execute(SCRIPT, List.of(key), args)
                .next()
                .map(RateLimitFilter::decision)
                .onErrorResume(ex -> {
                    log.warn("Rate limit check skipped, Redis unavailable: {}", ex.getMessage());
                    return Mono.just(UNCHECKED);
                })
                .defaultIfEmpty(UNCHECKED)
                .flatMap(decision -> {
                    if (decision != UNCHECKED) {
                        exchange.getResponse().getHeaders().set("X-RateLimit-Limit", String.valueOf(tier.getBurst()));
                        exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
                    }
                    return decision.allowed() ? chain.filter(exchange) : reject(exchange, decision.retryAfterSeconds());
                });
    }

    RateLimitProperties.Tier tierFor(ServerHttpRequest request) {
        PathContainer path = PathContainer.parsePath(request.getPath().value());
        for (CompiledTier tier : tiers) {
            if (tier.matches(request.getMethod(), path)) return tier.tier();
        }
        return null;
    }

    /** The verified user, else the client address. X-Real-IP is set (never passed through)
     *  by the web container's nginx, the only way in from outside. */
    static String callerKey(ServerWebExchange exchange) {
        Object caller = exchange.getAttribute(JwtBackstopFilter.CALLER_ATTRIBUTE);
        if (caller != null) return "u:" + caller;
        String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return "ip:" + realIp.trim();
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return "ip:" + (remote == null ? "unknown" : remote.getAddress().getHostAddress());
    }

    private static Decision decision(List<?> result) {
        return new Decision(((Number) result.get(0)).longValue() == 1,
                ((Number) result.get(1)).longValue(),
                ((Number) result.get(2)).longValue());
    }

    private Mono<Void> reject(ServerWebExchange exchange, long retryAfterSeconds) {
        long wait = Math.max(1, retryAfterSeconds);
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(wait));
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
        String message = "Too many requests — please wait " + wait + " second" + (wait == 1 ? "" : "s")
                + " and try again.";
        byte[] body = ("{\"message\":\"" + message + "\",\"code\":\"RATE_LIMITED\"}").getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }
}
