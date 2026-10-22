package com.nerd.erp.infrastructure.config.core;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nerd.bootstrap")
public record BootstrapProperties(
        boolean enabled,
        String adminEmail,
        String adminPassword,
        String tenantName,
        String tenantSlug
) {
}
