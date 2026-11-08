package com.forgepilot.system.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "forgepilot.jwt")
public class JwtProperties {

    /** HS256 secret for local/dev. Override in real deployments. */
    private String secret = "forgepilot-local-dev-secret-change-me-32b";
    private long expireSeconds = 86_400;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }

    public void setExpireSeconds(long expireSeconds) {
        this.expireSeconds = expireSeconds;
    }
}
