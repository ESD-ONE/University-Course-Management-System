package com.ucms.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where to fetch Keycloak's signing keys, and the issuer tokens must carry. {@code url} is
 * how the Gateway reaches Keycloak (inside Docker: http://ucms-keycloak:8080/auth); the
 * issuer is the public URL browsers use, which can differ.
 */
@ConfigurationProperties(prefix = "ucms.keycloak")
public class KeycloakProperties {

    private String url;
    private String realm = "ucms";
    private String issuer;

    public String realmUrl() { return url + "/realms/" + realm; }
    public String jwksUri() { return realmUrl() + "/protocol/openid-connect/certs"; }
    public String expectedIssuer() { return issuer != null && !issuer.isBlank() ? issuer : realmUrl(); }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getRealm() { return realm; }
    public void setRealm(String realm) { this.realm = realm; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
}
