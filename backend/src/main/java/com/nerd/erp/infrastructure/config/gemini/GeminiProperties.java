package com.nerd.erp.infrastructure.config.gemini;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nerd.gemini")
public record GeminiProperties(
        String apiKey,
        String model,
        boolean enabled
) {
    public GeminiProperties {
        if (model == null || model.isBlank()) {
            model = "gemini-2.5-flash";
        }
    }

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
