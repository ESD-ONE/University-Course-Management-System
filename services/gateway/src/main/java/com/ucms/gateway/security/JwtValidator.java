package com.ucms.gateway.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Signature + expiry + issuer check only — the Gateway does not look at roles, scopes, or
 * any claim beyond what's needed to confirm Keycloak issued the token to a UCMS client and
 * it hasn't expired. Every backend service still independently enforces its own
 * role/scope authorization on every request; this exists purely as a backstop (Gap G-18)
 * so a future service added to the routes table isn't silently open to the internet if
 * it forgets to add its own auth filter. Keycloak's signing keys are fetched once and
 * cached, and re-fetched when a token arrives signed with a key not seen before.
 */
@Service
@EnableConfigurationProperties(KeycloakProperties.class)
public class JwtValidator {

    private final ReactiveJwtDecoder decoder;

    public JwtValidator(KeycloakProperties properties) {
        NimbusReactiveJwtDecoder nimbus = NimbusReactiveJwtDecoder.withJwkSetUri(properties.jwksUri()).build();
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.expectedIssuer()),
                JwtValidator::issuedToUcmsClient));
        this.decoder = nimbus;
    }

    /** Errors if the signature doesn't verify, the token has expired, or it isn't ours. */
    public Mono<Jwt> verify(String token) {
        return decoder.decode(token);
    }

    private static OAuth2TokenValidatorResult issuedToUcmsClient(Jwt jwt) {
        String azp = jwt.getClaimAsString("azp");
        if ("ucms-web".equals(azp) || (azp != null && azp.startsWith("svc-"))) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Token was not issued to a UCMS client", null));
    }
}
