package com.ucms.academic.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ucms.jwt")
public class JwtProperties {
    private String secret;

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
}
