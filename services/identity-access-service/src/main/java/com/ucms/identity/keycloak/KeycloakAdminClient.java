package com.ucms.identity.keycloak;

import com.ucms.identity.security.ClientCredentialsTokenProvider;
import com.ucms.identity.security.KeycloakProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Thin wrapper over the parts of Keycloak's Admin REST API this service uses, called as
 * the svc-identity-access service account (which holds realm-management's
 * manage-users/view-users/query-users roles and nothing more).
 */
@Component
public class KeycloakAdminClient {

    static final List<String> ACCOUNT_ROLES = List.of("ADMIN", "LECTURER", "STUDENT");
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAPS =
            new ParameterizedTypeReference<>() {};

    private final KeycloakProperties properties;
    private final ClientCredentialsTokenProvider tokens;
    private final RestClient restClient;

    public KeycloakAdminClient(KeycloakProperties properties, ClientCredentialsTokenProvider tokens) {
        this.properties = properties;
        this.tokens = tokens;
        this.restClient = RestClient.create();
    }

    private String admin(String path) {
        return properties.getUrl() + "/admin/realms/" + properties.getRealm() + path;
    }

    /** Creates the user; returns Keycloak's id for it. */
    public UUID createUser(Map<String, Object> representation) {
        return call("create user", () -> {
            URI location = restClient.post().uri(admin("/users"))
                    .header("Authorization", "Bearer " + tokens.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(representation)
                    .retrieve().toBodilessEntity().getHeaders().getLocation();
            if (location == null) throw new IllegalStateException("Keycloak returned no Location for a new user");
            String path = location.getPath();
            return UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
        });
    }

    public Optional<UUID> findByUcmsId(UUID ucmsId) {
        return findOne("/users?exact=true&q=ucms_uid:" + ucmsId);
    }

    public Optional<UUID> findByUsername(String username) {
        return findOne("/users?exact=true&username={username}", username);
    }

    private Optional<UUID> findOne(String query, Object... vars) {
        return call("find user", () -> {
            List<Map<String, Object>> users = restClient.get().uri(admin(query), vars)
                    .header("Authorization", "Bearer " + tokens.token())
                    .retrieve().body(LIST_OF_MAPS);
            return users == null || users.isEmpty()
                    ? Optional.<UUID>empty()
                    : Optional.of(UUID.fromString((String) users.get(0).get("id")));
        });
    }

    public void updateUser(UUID keycloakId, Map<String, Object> representation) {
        call("update user", () -> restClient.put().uri(admin("/users/{id}"), keycloakId)
                .header("Authorization", "Bearer " + tokens.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(representation)
                .retrieve().toBodilessEntity());
    }

    /** Idempotent: an already-deleted user is not an error. */
    public void deleteUser(UUID keycloakId) {
        call("delete user", () -> {
            try {
                return restClient.delete().uri(admin("/users/{id}"), keycloakId)
                        .header("Authorization", "Bearer " + tokens.token())
                        .retrieve().toBodilessEntity();
            } catch (HttpClientErrorException.NotFound gone) {
                return null;
            }
        });
    }

    /** Ends every session the user has, so a suspension takes effect at their next token refresh. */
    public void logoutSessions(UUID keycloakId) {
        call("end sessions", () -> restClient.post().uri(admin("/users/{id}/logout"), keycloakId)
                .header("Authorization", "Bearer " + tokens.token())
                .retrieve().toBodilessEntity());
    }

    public void resetPassword(UUID keycloakId, String rawPassword) {
        call("set password", () -> restClient.put().uri(admin("/users/{id}/reset-password"), keycloakId)
                .header("Authorization", "Bearer " + tokens.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", "password", "value", rawPassword, "temporary", false))
                .retrieve().toBodilessEntity());
    }

    /** Leaves the user holding exactly one of ADMIN/LECTURER/STUDENT: {@code role}. */
    public void setAccountRole(UUID keycloakId, String role) {
        call("set role", () -> {
            List<Map<String, Object>> current = restClient.get()
                    .uri(admin("/users/{id}/role-mappings/realm"), keycloakId)
                    .header("Authorization", "Bearer " + tokens.token())
                    .retrieve().body(LIST_OF_MAPS);
            List<Map<String, Object>> stale = current == null ? List.of() : current.stream()
                    .filter(r -> ACCOUNT_ROLES.contains((String) r.get("name")) && !role.equals(r.get("name")))
                    .toList();
            if (!stale.isEmpty()) {
                restClient.method(org.springframework.http.HttpMethod.DELETE)
                        .uri(admin("/users/{id}/role-mappings/realm"), keycloakId)
                        .header("Authorization", "Bearer " + tokens.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(stale)
                        .retrieve().toBodilessEntity();
            }
            boolean hasRole = current != null && current.stream().anyMatch(r -> role.equals(r.get("name")));
            if (!hasRole) {
                // Read from the user's assignable roles: GET /roles/{name} would need the
                // broader view-realm permission, which this service account doesn't hold.
                List<Map<String, Object>> available = restClient.get()
                        .uri(admin("/users/{id}/role-mappings/realm/available"), keycloakId)
                        .header("Authorization", "Bearer " + tokens.token())
                        .retrieve().body(LIST_OF_MAPS);
                Map<String, Object> roleRep = available == null ? null : available.stream()
                        .filter(r -> role.equals(r.get("name"))).findFirst().orElse(null);
                if (roleRep == null) throw new IllegalStateException("Realm role " + role + " not found in Keycloak");
                restClient.post().uri(admin("/users/{id}/role-mappings/realm"), keycloakId)
                        .header("Authorization", "Bearer " + tokens.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(List.of(roleRep))
                        .retrieve().toBodilessEntity();
            }
            return null;
        });
    }

    /**
     * Asks Keycloak whether this is the user's current password (a password grant on
     * svc-identity-access, used only server-side). Counts toward Keycloak's brute-force
     * lockout like any other sign-in attempt.
     */
    public boolean passwordMatches(String username, String rawPassword) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "password");
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("username", username);
        form.add("password", rawPassword);
        form.add("scope", "openid");
        return call("check password", () -> {
            try {
                restClient.post().uri(properties.tokenUri())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve().toBodilessEntity();
                return true;
            } catch (HttpClientErrorException ex) {
                if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED || ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
                    return false;
                }
                throw ex;
            }
        });
    }

    private <T> T call(String what, Supplier<T> action) {
        try {
            return action.get();
        } catch (RestClientException | IllegalStateException ex) {
            throw translate(what, ex);
        }
    }

    /** A 400 is Keycloak refusing the request itself (e.g. its password policy); anything
     *  else means it couldn't do its job. */
    static RuntimeException translate(String what, RuntimeException ex) {
        if (ex instanceof HttpClientErrorException.BadRequest bad) {
            Map<?, ?> body = null;
            try {
                body = bad.getResponseBodyAs(Map.class);
            } catch (RuntimeException ignored) {
                // Not JSON: fall back to a generic explanation.
            }
            Object reason = body == null ? null
                    : body.get("error_description") != null ? body.get("error_description") : body.get("errorMessage");
            return new KeycloakRejectedException(
                    reason instanceof String text && !text.isBlank() ? text : "The sign-in service rejected this change.", ex);
        }
        return new KeycloakUnavailableException("Keycloak: could not " + what + " — " + ex.getMessage(), ex);
    }
}
