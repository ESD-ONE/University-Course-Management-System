package com.ucms.notification.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where Keycloak is and who this service is to it. {@code url} is the address this
 * service reaches Keycloak on (inside Docker: http://ucms-keycloak:8080/auth), which can
 * differ from the public address browsers use — tokens carry the public one as their
 * issuer, so that is configured separately.
 */
@ConfigurationProperties(prefix = "ucms.keycloak")
public class KeycloakProperties {

    /** Base URL including Keycloak's /auth path, e.g. http://localhost:8180/auth. */
    private String url;
    private String realm = "ucms";
    /** Expected "iss" claim; defaults to {url}/realms/{realm}. */
    private String issuer;
    /** PEM public key to verify tokens with instead of fetching Keycloak's JWKS (tests). */
    private String publicKey;
    /** This service's own confidential client, for client_credentials tokens. */
    private String clientId;
    private String clientSecret;
    /** Realm role a service token must carry to be accepted here, e.g. "svc:identity". */
    private String serviceRole;

    public String realmUrl() { return url + "/realms/" + realm; }
    public String jwksUri() { return realmUrl() + "/protocol/openid-connect/certs"; }
    public String tokenUri() { return realmUrl() + "/protocol/openid-connect/token"; }
    public String expectedIssuer() { return issuer != null && !issuer.isBlank() ? issuer : realmUrl(); }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getRealm() { return realm; }
    public void setRealm(String realm) { this.realm = realm; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getServiceRole() { return serviceRole; }
    public void setServiceRole(String serviceRole) { this.serviceRole = serviceRole; }
}
