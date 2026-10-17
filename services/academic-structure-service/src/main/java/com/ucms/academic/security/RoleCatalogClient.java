package com.ucms.academic.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches Identity & Access's role -> baseline-permission-grant matrix locally, refreshed
 * periodically — docs/SYSTEM_ARCHITECTURE_REPORT.md section 12.3's "Phase 1" authz
 * strategy: no synchronous call to Identity & Access per request, only a periodic
 * background sync (permission *definitions* change rarely; scope *assignments* travel
 * fresh in the JWT itself, see CurrentAssignments).
 *
 * Authenticates the sync call with a short-lived, self-minted service token signed with
 * the same shared JWT_SECRET every service already trusts — reusing the existing trust
 * model rather than adding a new unauthenticated internal endpoint.
 */
@Component
public class RoleCatalogClient implements RoleCatalog {

    private static final Logger log = LoggerFactory.getLogger(RoleCatalogClient.class);

    private final RestClient restClient;
    private final SecretKey key;
    /** roleId -> "module|actionKey" -> granted */
    private final Map<UUID, Map<String, Boolean>> cache = new ConcurrentHashMap<>();

    public RoleCatalogClient(@Value("${ucms.identity-service-uri}") String identityServiceUri,
                              @Value("${ucms.jwt.secret}") String jwtSecret) {
        this.restClient = RestClient.builder().baseUrl(identityServiceUri).build();
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        refresh();
    }

    @Scheduled(fixedDelay = 60_000)
    public void refresh() {
        try {
            String serviceToken = mintServiceToken();
            RoleCatalogEntry[] roles = restClient.get()
                    .uri("/roles")
                    .header("Authorization", "Bearer " + serviceToken)
                    .retrieve()
                    .body(RoleCatalogEntry[].class);
            if (roles == null) return;
            Map<UUID, Map<String, Boolean>> fresh = new ConcurrentHashMap<>();
            for (RoleCatalogEntry role : roles) {
                Map<String, Boolean> grants = new ConcurrentHashMap<>();
                if (role.permissions() != null) {
                    for (RolePermissionGrant g : role.permissions()) {
                        grants.put(g.module() + "|" + g.actionKey(), g.granted());
                    }
                }
                fresh.put(role.id(), grants);
            }
            cache.clear();
            cache.putAll(fresh);
            log.info("Role permission catalog refreshed: {} roles", fresh.size());
        } catch (Exception ex) {
            log.warn("Could not refresh role permission catalog from Identity & Access — keeping previous cache ({} roles): {}",
                    cache.size(), ex.getMessage());
        }
    }

    /** Baseline grant for a role, before any per-assignment override is applied. */
    @Override
    public boolean baselineGrant(UUID roleId, String module, String actionKey) {
        Map<String, Boolean> grants = cache.get(roleId);
        return grants != null && Boolean.TRUE.equals(grants.get(module + "|" + actionKey));
    }

    private String mintServiceToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("00000000-0000-0000-0000-000000000000")
                .claim("email", "service@ucms.internal")
                .claim("role", "ADMIN")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(2, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RoleCatalogEntry(UUID id, String name, String kind, List<RolePermissionGrant> permissions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RolePermissionGrant(UUID permissionId, String module, String actionKey, boolean granted) {}
}
