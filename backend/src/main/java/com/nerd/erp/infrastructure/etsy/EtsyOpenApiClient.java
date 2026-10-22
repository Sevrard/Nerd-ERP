package com.nerd.erp.infrastructure.etsy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.infrastructure.config.etsy.EtsyProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Etsy Open API v3 (aligné MCP Etsy : findAllActiveListingsByShop, getShopReceipts,
 * getShopReceiptTransactionsByReceipt — pagination limit/offset).
 */
@Component
public class EtsyOpenApiClient {

    private static final Logger log = LoggerFactory.getLogger(EtsyOpenApiClient.class);
    private static final int PAGE_SIZE = 100;
    /** Espacement minimal entre appels /users (limite Etsy ~10 req/s). */
    private static final long USER_API_PAUSE_MS = 200;

    private final EtsyProperties etsyProperties;
    private final EtsyAccessTokenHolder tokenHolder;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public EtsyOpenApiClient(EtsyProperties etsyProperties,
                             EtsyAccessTokenHolder tokenHolder,
                             ObjectMapper objectMapper) {
        this.etsyProperties = etsyProperties;
        this.tokenHolder = tokenHolder;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl("https://openapi.etsy.com").build();
    }

    public void requireConfigured() {
        if (!etsyProperties.isConfigured()) {
            String hint = etsyProperties.hasCompleteApiKey()
                    ? "api-key, access-token, shop-id"
                    : "api-key + shared-secret (ou api-key au format keystring:shared_secret), access-token, shop-id";
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "API Etsy non configurée (nerd.etsy : " + hint + ")");
        }
    }

    public List<JsonNode> fetchAllActiveListings() {
        requireConfigured();
        return fetchAllPages("/v3/application/shops/" + etsyProperties.shopId() + "/listings/active", Map.of());
    }

    public List<JsonNode> fetchAllReceipts() {
        requireConfigured();
        return fetchAllPages("/v3/application/shops/" + etsyProperties.shopId() + "/receipts", Map.of(
                "sort_on", "created", "sort_order", "desc"));
    }

    public List<JsonNode> fetchReceiptTransactions(long receiptId) {
        requireConfigured();
        return fetchAllPages(
                "/v3/application/shops/" + etsyProperties.shopId() + "/receipts/" + receiptId + "/transactions",
                Map.of());
    }

    /**
     * Profil utilisateur Etsy (scope {@code email_r} pour {@code primary_email}).
     * 404/403 → vide sans interrompre la sync.
     */
    public Optional<JsonNode> fetchUserProfile(long userId) {
        if (userId <= 0) {
            return Optional.empty();
        }
        requireConfigured();
        pauseBeforeUserApi();
        return getJsonOptional("/v3/application/users/" + userId);
    }

    public Optional<String> fetchUserPrimaryEmail(long userId) {
        Optional<JsonNode> profile = fetchUserProfile(userId);
        if (profile.isEmpty()) {
            return Optional.empty();
        }
        JsonNode node = profile.get();
        String email = readEmailField(node, "primary_email");
        if (email.isBlank()) {
            email = readEmailField(node, "email");
        }
        if (email.isBlank()) {
            log.debug("Profil Etsy user {} sans primary_email (droits app ou scope email_r)", userId);
            return Optional.empty();
        }
        return Optional.of(email);
    }

    private static String readEmailField(JsonNode node, String field) {
        JsonNode email = node.get(field);
        if (email == null || email.isNull()) {
            return "";
        }
        return email.asText("").trim();
    }

    private void pauseBeforeUserApi() {
        try {
            Thread.sleep(USER_API_PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private List<JsonNode> fetchAllPages(String path, Map<String, String> extraParams) {
        List<JsonNode> collected = new ArrayList<>();
        int offset = 0;
        while (true) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path)
                    .queryParam("limit", PAGE_SIZE).queryParam("offset", offset);
            extraParams.forEach(builder::queryParam);
            JsonNode body = getJson(builder.build().toUriString());
            JsonNode results = body.get("results");
            int pageCount = 0;
            if (results != null && results.isArray()) {
                for (JsonNode node : results) {
                    collected.add(node);
                    pageCount++;
                }
            }
            if (pageCount == 0 || pageCount < PAGE_SIZE) {
                break;
            }
            offset += pageCount;
        }
        return collected;
    }

    private Optional<JsonNode> getJsonOptional(String uri) {
        return getJsonOptional(uri, true);
    }

    private Optional<JsonNode> getJsonOptional(String uri, boolean allowRefresh) {
        try {
            String raw = restClient.get().uri(uri).accept(MediaType.APPLICATION_JSON)
                    .header("x-api-key", etsyProperties.xApiKeyHeaderValue())
                    .header("Authorization", "Bearer " + tokenHolder.accessToken())
                    .retrieve().body(String.class);
            return Optional.of(objectMapper.readTree(raw));
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            if (code == 404 || code == 403) {
                log.debug("Etsy {} sur {} : {}", code, uri, truncate(e.getResponseBodyAsString()));
                return Optional.empty();
            }
            if (allowRefresh && code == 401 && tokenHolder.canRefresh() && tokenHolder.refreshAccessToken()) {
                return getJsonOptional(uri, false);
            }
            throw toApiException(e);
        } catch (Exception e) {
            log.warn("Etsy lookup ignoré ({}): {}", uri, e.getMessage());
            return Optional.empty();
        }
    }

    private JsonNode getJson(String uri) {
        return getJson(uri, true);
    }

    private JsonNode getJson(String uri, boolean allowRefresh) {
        try {
            String raw = restClient.get().uri(uri).accept(MediaType.APPLICATION_JSON)
                    .header("x-api-key", etsyProperties.xApiKeyHeaderValue())
                    .header("Authorization", "Bearer " + tokenHolder.accessToken())
                    .retrieve().body(String.class);
            return objectMapper.readTree(raw);
        } catch (RestClientResponseException e) {
            if (allowRefresh && e.getStatusCode().value() == 401 && tokenHolder.canRefresh()
                    && tokenHolder.refreshAccessToken()) {
                return getJson(uri, false);
            }
            throw toApiException(e);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Appel API Etsy échoué : " + e.getMessage());
        }
    }

    private ApiException toApiException(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (e.getStatusCode().value() == 401) {
            String hint = tokenHolder.canRefresh()
                    ? "Renouvellement automatique impossible (refresh token invalide ou expiré). "
                    + "Relancez : cd backend && node scripts/etsy-oauth.mjs"
                    : "Access token expiré. Ajoutez nerd.etsy.refresh-token puis "
                    + "node scripts/etsy-refresh-token.mjs (ou node scripts/etsy-oauth.mjs).";
            return new ApiException(HttpStatus.UNAUTHORIZED, "Etsy : session expirée. " + hint);
        }
        return new ApiException(HttpStatus.BAD_GATEWAY,
                "Appel API Etsy échoué : " + e.getStatusCode() + " " + truncate(body));
    }

    private static String truncate(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        String oneLine = s.replace('\n', ' ').trim();
        return oneLine.length() > 200 ? oneLine.substring(0, 200) + "…" : oneLine;
    }
}
