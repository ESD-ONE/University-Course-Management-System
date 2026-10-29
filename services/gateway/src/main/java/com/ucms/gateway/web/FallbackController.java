package com.ucms.gateway.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Where a route's circuit breaker sends requests while that service is failing or has no
 *  healthy replica: a quick 503 in the system's usual error shape instead of a hang. */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/{service}")
    public ResponseEntity<Map<String, String>> unavailable(@PathVariable("service") String service) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "message", "This part of the system is temporarily unavailable — please try again in a moment.",
                "code", "SERVICE_UNAVAILABLE",
                "service", service));
    }
}
