package com.nerd.erp.infrastructure.etsy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nerd.erp.infrastructure.config.etsy.EtsyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * Access token OAuth Etsy (~1 h). Renouvelable via {@code refresh_token} (~90 j).
 */
@Component
public class EtsyAccessTokenHolder {

    private static final Logger log = LoggerFactory.getLogger(EtsyAccessTokenHolder.class);

    private final EtsyProperties etsyProperties;
    private final ObjectMapper objectMapper;
    private final RestClient oauthClient;

    private volatile String accessToken;
    private volatile String refreshToken;

    public EtsyAccessTokenHolder(EtsyProperties etsyProperties, ObjectMapper objectMapper) {
        this.etsyProperties = etsyProperties;
        this.objectMapper = objectMapper;
        this.oauthClient = RestClient.builder().baseUrl("https://api.etsy.com").build();
        this.accessToken = etsyProperties.accessToken();
        this.refreshToken = etsyProperties.refreshToken();
    }

    public String accessToken() {
        String current = accessToken;
        if (current != null && !current.isBlank()) {
            return current.trim();
        }
        return etsyProperties.accessToken() != null ? etsyProperties.accessToken().trim() : "";
    }

    public boolean canRefresh() {
        return refreshToken != null && !refreshToken.isBlank();
    }

    /**
     * @return true si un nouvel access token a été obtenu
     */
    public synchronized boolean refreshAccessToken() {
        if (!etsyProperties.hasCompleteApiKey()) {
            return false;
        }
        String rt = refreshToken;
        if (rt == null || rt.isBlank()) {
            return false;
        }
        try {
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "refresh_token");
            body.add("client_id", etsyProperties.keystringOnly());
            body.add("refresh_token", rt.trim());

            String raw = oauthClient.post()
                    .uri("/v3/public/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header("x-api-key", etsyProperties.xApiKeyHeaderValue())
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode json = objectMapper.readTree(raw);
            String newAccess = text(json, "access_token");
            if (newAccess == null || newAccess.isBlank()) {
                log.warn("Refresh Etsy : réponse sans access_token");
                return false;
            }
            accessToken = newAccess.trim();
            String newRefresh = text(json, "refresh_token");
            if (newRefresh != null && !newRefresh.isBlank()) {
                refreshToken = newRefresh.trim();
            }
            log.info("Access token Etsy renouvelé (expire dans {} s)", json.path("expires_in").asInt(-1));
            return true;
        } catch (Exception e) {
            log.warn("Refresh token Etsy échoué : {}", e.getMessage());
            return false;
        }
    }

    private static String text(JsonNode json, String field) {
        JsonNode n = json.get(field);
        return n != null && !n.isNull() ? n.asText() : null;
    }
}
