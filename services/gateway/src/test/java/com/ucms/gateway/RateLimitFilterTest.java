package com.ucms.gateway;

import com.ucms.gateway.ratelimit.RateLimitFilter;
import com.ucms.gateway.ratelimit.RateLimitProperties;
import com.ucms.gateway.security.JwtBackstopFilter;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Rate limiter behaviour that doesn't need a working Redis. */
class RateLimitFilterTest {

    private static RateLimitProperties.Tier tier(String name, int limit, Duration period, int burst,
                                                 List<String> methods, String... paths) {
        RateLimitProperties.Tier t = new RateLimitProperties.Tier();
        t.setName(name);
        t.setLimit(limit);
        t.setPeriod(period);
        t.setBurst(burst);
        t.setMethods(methods);
        t.setPaths(List.of(paths));
        return t;
    }

    @Test
    void perMinuteLimitsBecomeFractionalRefillRates() {
        assertThat(tier("x", 6, Duration.ofMinutes(1), 6, List.of()).tokensPerSecond()).isEqualTo(0.1);
        assertThat(tier("x", 20, Duration.ofSeconds(1), 40, List.of()).tokensPerSecond()).isEqualTo(20.0);
    }

    @Test
    void whenRedisIsDownRequestsAreLetThrough() throws IOException {
        int deadPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            deadPort = socket.getLocalPort();
        }
        LettuceConnectionFactory factory = new LettuceConnectionFactory("localhost", deadPort);
        factory.afterPropertiesSet();
        RateLimitProperties properties = new RateLimitProperties();
        properties.setTiers(List.of(tier("default", 1, Duration.ofSeconds(1), 1, List.of(), "/api/**")));
        RateLimitFilter filter = new RateLimitFilter(properties, new ReactiveStringRedisTemplate(factory));

        AtomicInteger forwarded = new AtomicInteger();
        GatewayFilterChain chain = ex -> {
            forwarded.incrementAndGet();
            return Mono.empty();
        };
        for (int i = 0; i < 3; i++) {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/base/x"));
            exchange.getAttributes().put(JwtBackstopFilter.CALLER_ATTRIBUTE, "u1");
            filter.filter(exchange, chain).block(Duration.ofSeconds(10));
            assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }
        assertThat(forwarded).hasValue(3);
        factory.destroy();
    }
}
