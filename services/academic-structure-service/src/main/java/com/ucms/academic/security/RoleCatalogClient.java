package com.ucms.academic.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches Identity & Access's role -> baseline-permission-grant matrix locally, refreshed
 * periodically — docs/SYSTEM_ARCHITECTURE_REPORT.md section 12.3's "Phase 1" authz
 * strategy: no synchronous call to Identity & Access per request, only a periodic
 * background sync (permission *definitions* change rarely; users' scope *assignments*
 * are cached per user, see AssignmentCache).
 *
 * Authenticates as this service's Keycloak service account (client_credentials), which
 * holds the "svc:identity" role identity-access-service requires of callers.
 */
@Component
public class RoleCatalogClient implements RoleCatalog {

    private static final Logger log = LoggerFactory.getLogger(RoleCatalogClient.class);

    private final RestClient restClient;
    private final JwtValidator jwt;
    /** roleId -> "module|actionKey" -> granted */
    private final Map<UUID, Map<String, Boolean>> cache = new ConcurrentHashMap<>();
    /** "module|actionKey" -> permissionId — the global permission catalog is the same
     * across every role, so this is populated once per refresh from whichever roles'
     * grants happen to carry each pair (see G-09: PermissionResolver.effectiveGrant()
     * needs this to resolve an assignment's override, which is keyed by permission id
     * rather than module/actionKey). */
    private final Map<String, UUID> permissionIdCache = new ConcurrentHashMap<>();

    public RoleCatalogClient(RestClient.Builder builder, @Value("${ucms.identity-service-uri}") String identityServiceUri,
                             JwtValidator jwt) {
        this.restClient = builder.baseUrl(identityServiceUri).build();
        this.jwt = jwt;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        refresh();
    }

    /** Until the first successful load, retry every 5 seconds rather than every minute:
     *  with an empty catalog every permission check is denied, so a replica that starts
     *  while Identity & Access is still coming up would refuse everything for a minute. */
    @Scheduled(initialDelay = 5_000, fixedDelay = 5_000)
    public void refreshWhileEmpty() {
        if (cache.isEmpty()) refresh();
    }

    @Scheduled(fixedDelay = 60_000)
    public void refresh() {
        try {
            String serviceToken = jwt.mintServiceToken();
            RoleCatalogEntry[] roles = restClient.get()
                    .uri("/roles")
                    .header("Authorization", "Bearer " + serviceToken)
                    .retrieve()
                    .body(RoleCatalogEntry[].class);
            if (roles == null) return;
            Map<UUID, Map<String, Boolean>> fresh = new ConcurrentHashMap<>();
            Map<String, UUID> freshPermissionIds = new ConcurrentHashMap<>();
            for (RoleCatalogEntry role : roles) {
                Map<String, Boolean> grants = new ConcurrentHashMap<>();
                if (role.permissions() != null) {
                    for (RolePermissionGrant g : role.permissions()) {
                        String grantKey = g.module() + "|" + g.actionKey();
                        grants.put(grantKey, g.granted());
                        freshPermissionIds.put(grantKey, g.permissionId());
                    }
                }
                fresh.put(role.id(), grants);
            }
            cache.clear();
            cache.putAll(fresh);
            permissionIdCache.clear();
            permissionIdCache.putAll(freshPermissionIds);
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

    @Override
    public UUID permissionIdOf(String module, String actionKey) {
        return permissionIdCache.get(module + "|" + actionKey);
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RoleCatalogEntry(UUID id, String name, String kind, List<RolePermissionGrant> permissions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RolePermissionGrant(UUID permissionId, String module, String actionKey, boolean granted) {}
}
