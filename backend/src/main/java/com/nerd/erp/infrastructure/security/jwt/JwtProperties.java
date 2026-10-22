package com.nerd.erp.infrastructure.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nerd.security.jwt")
public record JwtProperties(String secret, long expirationMs) {
}
