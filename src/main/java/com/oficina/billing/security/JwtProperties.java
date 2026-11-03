package com.oficina.billing.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String secret, Staff staff) {
    public record Staff(String issuer, String audience, String keyId) {}
}
