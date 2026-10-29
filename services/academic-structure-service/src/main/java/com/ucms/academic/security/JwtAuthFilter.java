package com.ucms.academic.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    /** identity-access-service's seeded Administrator role (V2 seed data). */
    private static final UUID ADMINISTRATOR_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final JwtValidator jwtValidator;
    private final AssignmentCache assignmentCache;

    public JwtAuthFilter(JwtValidator jwtValidator, AssignmentCache assignmentCache) {
        this.jwtValidator = jwtValidator;
        this.assignmentCache = assignmentCache;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                VerifiedToken caller = jwtValidator.verify(header.substring(7));
                var authToken = new UsernamePasswordAuthenticationToken(
                        caller.subject(), null, List.of(new SimpleGrantedAuthority("ROLE_" + caller.role())));
                // Scope assignments travel in Authentication.details (not the principal)
                // so every existing @AuthenticationPrincipal UUID usage keeps working
                // unchanged — see CurrentAssignments.
                authToken.setDetails(assignmentsFor(caller));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * A trusted service acts as a GLOBAL administrator here, as its self-signed token did
     * before Keycloak (base-service's department-dependents check must see every
     * department's entries). A token that itself carries an "assignments" claim is used
     * as-is — Keycloak's don't, but a protocol mapper could add one. Otherwise the
     * person's assignments are loaded from AssignmentCache when first needed.
     */
    private Object assignmentsFor(VerifiedToken caller) {
        if (caller.service()) {
            return List.of(new AssignmentClaim(caller.subject(), ADMINISTRATOR_ROLE_ID, "GLOBAL", null, Map.of()));
        }
        Object claim = caller.jwt().getClaims().get("assignments");
        if (claim instanceof List<?> list) {
            return parseClaim(list);
        }
        UUID userId = caller.subject();
        return new CurrentAssignments.Lazy(() -> assignmentCache.forUser(userId));
    }

    /** Tolerates malformed entries (skipped) rather than failing authentication. */
    private static List<AssignmentClaim> parseClaim(List<?> raw) {
        List<AssignmentClaim> result = new ArrayList<>();
        for (Object item : raw) {
            if (!(item instanceof Map<?, ?> map)) continue;
            try {
                UUID id = UUID.fromString((String) map.get("id"));
                UUID roleId = UUID.fromString((String) map.get("roleId"));
                String scopeType = (String) map.get("scopeType");
                String scopeIdStr = (String) map.get("scopeId");
                UUID scopeId = (scopeIdStr == null || scopeIdStr.isEmpty()) ? null : UUID.fromString(scopeIdStr);
                Map<UUID, Boolean> overrides = new HashMap<>();
                if (map.get("overrides") instanceof Map<?, ?> overrideMap) {
                    overrideMap.forEach((k, v) -> overrides.put(UUID.fromString((String) k), (Boolean) v));
                }
                result.add(new AssignmentClaim(id, roleId, scopeType, scopeId, overrides));
            } catch (Exception ignored) {
                // skip malformed entries rather than failing authentication entirely
            }
        }
        return result;
    }
}
