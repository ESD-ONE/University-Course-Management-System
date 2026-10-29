package com.ucms.gateway;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/** A stand-in backend service: healthy, and answers every other path with 200 + its name. */
final class StubService implements AutoCloseable {

    final AtomicInteger hits = new AtomicInteger();
    private final HttpServer server;

    StubService(String name) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            boolean health = exchange.getRequestURI().getPath().equals("/actuator/health");
            if (!health) hits.incrementAndGet();
            String requestId = exchange.getRequestHeaders().getFirst("X-Request-Id");
            byte[] body = (health ? "{\"status\":\"UP\"}"
                    : "{\"service\":\"" + name + "\",\"path\":\"" + exchange.getRequestURI().getPath()
                      + "\",\"requestId\":\"" + requestId + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    String uri() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
