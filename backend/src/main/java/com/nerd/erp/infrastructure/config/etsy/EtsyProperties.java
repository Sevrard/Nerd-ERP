package com.nerd.erp.infrastructure.config.etsy;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nerd.etsy")
public record EtsyProperties(
        String apiKey,
        String sharedSecret,
        String accessToken,
        String refreshToken,
        Long shopId,
        boolean enabled) {

    /** Keystring seul (partie avant {@code :} si api-key combiné). */
    public String keystringOnly() {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        String trimmed = apiKey.trim();
        int colon = trimmed.indexOf(':');
        return colon > 0 ? trimmed.substring(0, colon) : trimmed;
    }

    /** Valeur attendue par Etsy dans {@code x-api-key} : {@code keystring:shared_secret}. */
    public String xApiKeyHeaderValue() {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        if (apiKey.contains(":")) {
            return apiKey.trim();
        }
        if (sharedSecret != null && !sharedSecret.isBlank()) {
            return apiKey.trim() + ":" + sharedSecret.trim();
        }
        return apiKey.trim();
    }

    public boolean hasCompleteApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }
        return apiKey.contains(":")
                || (sharedSecret != null && !sharedSecret.isBlank());
    }

    public boolean isConfigured() {
        return enabled && hasCompleteApiKey()
                && accessToken != null && !accessToken.isBlank()
                && shopId != null && shopId > 0;
    }
}
