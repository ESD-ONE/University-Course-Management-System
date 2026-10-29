package com.ucms.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The design's "WebSocket Gateway" (flow-01: "MQ-)WS: consume StudentEnrolled /
 * WS--)Client: live push"), implemented as Server-Sent Events from this service: the
 * push is one-directional (server → browser), so SSE covers it without a protocol
 * upgrade, travels through the existing Gateway and nginx like any other HTTP request,
 * and authenticates with the normal Authorization header — so the Gateway's JWT and
 * revocation checks apply to it unchanged.
 *
 * Connections are held in this replica's memory, but a push is broadcast to every
 * replica through Redis ({@link LivePushFanout}) and each delivers it to the browsers it
 * holds — so it reaches the user whichever replica their stream landed on. Without the
 * fan-out (or if Redis can't be reached) it's delivered locally only.
 */
@Component
public class LivePushHub {

    private static final Logger log = LoggerFactory.getLogger(LivePushHub.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<UUID, List<SseEmitter>> connections = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Optional<LivePushFanout> fanout;

    public LivePushHub(ObjectMapper objectMapper, Optional<LivePushFanout> fanout) {
        this.objectMapper = objectMapper;
        this.fanout = fanout;
    }

    public SseEmitter connect(UUID userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        connections.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable remove = () -> {
            List<SseEmitter> list = connections.get(userId);
            if (list != null) list.remove(emitter);
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("ready").data("connected"));
        } catch (IOException e) {
            remove.run();
        }
        return emitter;
    }

    public void push(UUID userId, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Live push for {} skipped, payload not serializable: {}", userId, e.getMessage());
            return;
        }
        if (fanout.isPresent() && fanout.get().publish(userId, json)) return;
        deliver(userId, json);
    }

    /** Sends to the browsers connected to this replica (called for every broadcast push). */
    public void deliver(UUID userId, String json) {
        List<SseEmitter> list = connections.get(userId);
        if (list == null) return;
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(json));
            } catch (Exception ex) {
                list.remove(emitter);
            }
        }
    }

    /** Keeps idle connections alive through proxies that close quiet streams. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        connections.forEach((userId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                } catch (Exception ex) {
                    list.remove(emitter);
                }
            }
        });
    }
}
