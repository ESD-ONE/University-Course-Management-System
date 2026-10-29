package com.ucms.academic.security;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

/**
 * This service's own access token for calling other services (or Keycloak's admin API),
 * obtained from Keycloak with the client_credentials grant and reused until shortly
 * before it expires. Replaces the HS256 "service token" each service used to sign for
 * itself with the shared secret, which carried blanket administrator rights everywhere.
 */
@Component
public class ClientCredentialsTokenProvider {

    private static final long REFRESH_MARGIN_SECONDS = 30;

    private final KeycloakProperties properties;
    private final RestClient restClient = RestClient.create();
    private String token;
    private Instant expiresAt = Instant.EPOCH;

    public ClientCredentialsTokenProvider(KeycloakProperties properties) {
        this.properties = properties;
    }

    public synchronized String token() {
        if (token != null && Instant.now().isBefore(expiresAt.minusSeconds(REFRESH_MARGIN_SECONDS))) {
            return token;
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        Map<?, ?> response = restClient.post()
                .uri(properties.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        if (response == null || !(response.get("access_token") instanceof String accessToken)) {
            throw new IllegalStateException("Keycloak returned no access token for " + properties.getClientId());
        }
        long lifetime = response.get("expires_in") instanceof Number n ? n.longValue() : 60;
        token = accessToken;
        // Never reuse a token past its own "exp": if Keycloak's clock lags ours, a token it
        // has just issued can already be expired here, and caching it for expires_in would
        // make every call fail until then. Capped by exp, it's simply fetched again.
        Instant byLifetime = Instant.now().plusSeconds(lifetime);
        Instant byClaim = expiryClaim(accessToken);
        expiresAt = byClaim != null && byClaim.isBefore(byLifetime) ? byClaim : byLifetime;
        return token;
    }

    /** The token's "exp" claim, read without verifying (it's our own, fresh from Keycloak). */
    private static Instant expiryClaim(String jwt) {
        try {
            String payload = new String(java.util.Base64.getUrlDecoder().decode(jwt.split("\\.")[1]),
                    java.nio.charset.StandardCharsets.UTF_8);
            var matcher = java.util.regex.Pattern.compile("\"exp\"\\s*:\\s*(\\d+)").matcher(payload);
            return matcher.find() ? Instant.ofEpochSecond(Long.parseLong(matcher.group(1))) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
