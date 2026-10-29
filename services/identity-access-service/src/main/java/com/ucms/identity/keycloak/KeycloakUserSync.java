package com.ucms.identity.keycloak;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucms.identity.domain.User;
import com.ucms.identity.domain.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mirrors UCMS accounts into Keycloak, which now verifies every sign-in. This service
 * stays the system of record for accounts (the Users screen, roles, status); Keycloak
 * holds what it needs to log someone in: username (= email), name, enabled flag, account
 * role, password, and the "ucms_uid" attribute that becomes the "uid" claim in tokens.
 *
 * Every method is called inside the account change's own transaction, so if Keycloak
 * can't be updated the change is rolled back instead of the two drifting apart.
 */
@Service
public class KeycloakUserSync {

    private static final Logger log = LoggerFactory.getLogger(KeycloakUserSync.class);

    private final KeycloakAdminClient keycloak;
    private final ObjectMapper objectMapper;

    public KeycloakUserSync(KeycloakAdminClient keycloak, ObjectMapper objectMapper) {
        this.keycloak = keycloak;
        this.objectMapper = objectMapper;
    }

    /** A new account: created in Keycloak with the password it was just given. */
    public void created(User user, String rawPassword) {
        Map<String, Object> rep = representation(user);
        rep.put("credentials", List.of(Map.of("type", "password", "value", rawPassword, "temporary", false)));
        UUID keycloakId = keycloak.createUser(rep);
        keycloak.setAccountRole(keycloakId, user.getRole().getKind().name());
        user.setKeycloakId(keycloakId);
    }

    /**
     * Links an account that has no Keycloak user yet — how existing accounts are migrated.
     * Adopts a Keycloak user already carrying this account's id or email (e.g. left by an
     * earlier, interrupted run); otherwise creates one with the account's existing BCrypt
     * hash, which Keycloak verifies through the bcrypt provider and upgrades on first login.
     */
    public void link(User user) {
        UUID keycloakId = keycloak.findByUcmsId(user.getId())
                .or(() -> keycloak.findByUsername(user.getEmail()))
                .orElse(null);
        if (keycloakId == null) {
            Map<String, Object> rep = representation(user);
            rep.put("credentials", List.of(bcryptCredential(user.getPasswordHash())));
            keycloakId = keycloak.createUser(rep);
            log.info("Migrated account {} to Keycloak", user.getEmail());
        } else {
            keycloak.updateUser(keycloakId, representation(user));
            log.info("Linked account {} to its existing Keycloak user", user.getEmail());
        }
        keycloak.setAccountRole(keycloakId, user.getRole().getKind().name());
        user.setKeycloakId(keycloakId);
    }

    public void profileChanged(User user) {
        if (user.getKeycloakId() == null) return;
        keycloak.updateUser(user.getKeycloakId(), representation(user));
    }

    /** Enabled only while ACTIVE; disabling also ends the user's open sessions. */
    public void statusChanged(User user) {
        if (user.getKeycloakId() == null) return;
        keycloak.updateUser(user.getKeycloakId(), representation(user));
        if (user.getStatus() != UserStatus.ACTIVE) {
            keycloak.logoutSessions(user.getKeycloakId());
        }
    }

    public void roleChanged(User user) {
        if (user.getKeycloakId() == null) return;
        keycloak.setAccountRole(user.getKeycloakId(), user.getRole().getKind().name());
    }

    public boolean passwordMatches(User user, String rawPassword) {
        return keycloak.passwordMatches(user.getEmail(), rawPassword);
    }

    public void passwordChanged(User user, String rawPassword) {
        if (user.getKeycloakId() == null) return;
        keycloak.resetPassword(user.getKeycloakId(), rawPassword);
    }

    public void deleted(User user) {
        if (user.getKeycloakId() == null) return;
        keycloak.deleteUser(user.getKeycloakId());
    }

    private Map<String, Object> representation(User user) {
        String[] names = splitName(user.getName());
        Map<String, Object> rep = new HashMap<>();
        rep.put("username", user.getEmail());
        rep.put("email", user.getEmail());
        rep.put("emailVerified", true);
        rep.put("firstName", names[0]);
        rep.put("lastName", names[1]);
        rep.put("enabled", user.getStatus() == UserStatus.ACTIVE);
        rep.put("attributes", Map.of("ucms_uid", List.of(user.getId().toString())));
        return rep;
    }

    /** UCMS stores one name; Keycloak wants first/last. "Ada Lovelace King" -> "Ada Lovelace" / "King". */
    static String[] splitName(String name) {
        String trimmed = name == null ? "" : name.trim();
        int cut = trimmed.lastIndexOf(' ');
        return cut < 0 ? new String[]{trimmed, ""} : new String[]{trimmed.substring(0, cut), trimmed.substring(cut + 1)};
    }

    private Map<String, Object> bcryptCredential(String hash) {
        try {
            return Map.of(
                    "type", "password",
                    "secretData", objectMapper.writeValueAsString(Map.of("value", hash, "salt", "")),
                    "credentialData", objectMapper.writeValueAsString(
                            Map.of("hashIterations", 10, "algorithm", "bcrypt", "additionalParameters", Map.of())));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
