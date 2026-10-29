package com.ucms.gateway.web;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/** Gives every request an X-Request-Id (nginx's, if it sent a sane one), passed to the
 *  service and echoed to the client, so one request can be followed through every log. */
@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Request-Id";
    private static final Pattern SANE = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
        String id = incoming != null && SANE.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set(HEADER, id);
        return chain.filter(exchange.mutate()
                .request(r -> r.headers(h -> h.set(HEADER, id)))
                .build());
    }
}
