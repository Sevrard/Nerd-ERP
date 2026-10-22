package com.nerd.erp.infrastructure.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.application.invoicescan.ExtractedInvoiceData;
import com.nerd.erp.infrastructure.config.gemini.GeminiProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class GeminiInvoiceExtractor {

    private static final BigDecimal DEFAULT_VAT_PERCENT = new BigDecimal("20");

    private static final String DEFAULT_SYSTEM_PROMPT = """
            Tu es un extracteur de données comptables pour un ERP français.
            Analyse le PDF de facture ou reçu fourni et renvoie UNIQUEMENT un objet JSON valide (sans markdown).
            Schéma strict :
            {
              "invoiceNumber": string | null,
              "orderNumber": string | null,
              "merchant": string | null,
              "issueDate": "YYYY-MM-DD" | null,
              "customer": {
                "customerType": "COMPANY" | "INDIVIDUAL",
                "companyName": string | null,
                "firstName": string | null,
                "lastName": string | null,
                "email": string | null,
                "phone": string | null,
                "addressLine1": string | null,
                "addressLine2": string | null,
                "postalCode": string | null,
                "city": string | null,
                "countryCode": "FR" | string ISO-2
              },
              "lines": [
                {
                  "description": string,
                  "quantity": number,
                  "unitPriceHt": number,
                  "vatRate": number
                }
              ],
              "shippingAmountHt": number | null,
              "shippingVatRate": number | null,
              "totalHt": number | null,
              "totalVat": number | null,
              "totalTtc": number | null,
              "currency": "EUR"
            }
            Règles :
            - Montants en euros, décimaux (pas de symbole €).
            - unitPriceHt par ligne ; vatRate en pourcentage (ex. 20 pour 20 %% TVA, pas 0.20). Si TVA inconnue, 20 pour FR.
            - Si seul le TTC est visible, estime le HT avec le taux TVA indiqué ou 20%%.
            - Frais de port : shippingAmountHt séparé des lignes articles si identifiables.
            - Client = acheteur / destinataire, pas l'émetteur Nerd-Forge sauf facture fournisseur.
            - orderNumber : numéro de commande marketplace, distinct du numéro de facture/reçu.
            - merchant : plateforme ou marchand visible sur le document. Nom court, sans URL. null si indéterminé.
            """;

    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GeminiInvoiceExtractor(GeminiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
    }

    public ExtractedInvoiceData extract(byte[] pdfBytes) {
        return extract(pdfBytes, null);
    }

    public ExtractedInvoiceData extract(byte[] pdfBytes, String merchantExtractionPrompt) {
        if (!properties.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Analyse IA indisponible : configurez la variable GEMINI_API_KEY");
        }
        String systemPrompt = (merchantExtractionPrompt != null && !merchantExtractionPrompt.isBlank())
                ? merchantExtractionPrompt
                : DEFAULT_SYSTEM_PROMPT;

        String base64 = Base64.getEncoder().encodeToString(pdfBytes);
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(
                                Map.of("inline_data", Map.of(
                                        "mime_type", "application/pdf",
                                        "data", base64)),
                                Map.of("text", systemPrompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "temperature", 0.1));

        String path = "/v1beta/models/" + properties.model() + ":generateContent?key=" + properties.apiKey();
        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Échec de l'appel Gemini : " + e.getMessage());
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode textNode = root.at("/candidates/0/content/parts/0/text");
            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Gemini n'a pas renvoyé de contenu exploitable");
            }
            return parsePayload(textNode.asText());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Réponse Gemini illisible : " + e.getMessage());
        }
    }

    private ExtractedInvoiceData parsePayload(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        if (node.has("order_number") || node.has("items")) {
            return parseMarketplaceReceiptPayload(node);
        }
        return parseStandardPayload(node);
    }

    private ExtractedInvoiceData parseMarketplaceReceiptPayload(JsonNode node) {
        JsonNode customerNode = node.path("customer");
        NameParts nameParts = splitFullName(textOrNull(customerNode, "full_name"));
        ExtractedInvoiceData.ExtractedCustomer customer = new ExtractedInvoiceData.ExtractedCustomer(
                "INDIVIDUAL",
                null,
                nameParts.firstName(),
                nameParts.lastName(),
                null,
                null,
                textOrNull(customerNode, "address"),
                null,
                textOrNull(customerNode, "postal_code"),
                textOrNull(customerNode, "city"),
                countryToIso(textOrNull(customerNode, "country")));

        List<ExtractedInvoiceData.ExtractedLine> lines = new ArrayList<>();
        for (JsonNode itemNode : node.path("items")) {
            BigDecimal qty = decimalOrZero(itemNode, "quantity");
            if (qty.signum() <= 0) {
                qty = BigDecimal.ONE;
            }
            BigDecimal unitTtc = decimalOrZero(itemNode, "unit_price");
            BigDecimal unitHt = ttcToHt(unitTtc);
            lines.add(new ExtractedInvoiceData.ExtractedLine(
                    marketplaceItemDescription(itemNode),
                    qty,
                    unitHt,
                    DEFAULT_VAT_PERCENT));
        }

        BigDecimal discountTtc = decimalOrNull(node, "discount_amount");
        if (discountTtc != null && discountTtc.signum() > 0) {
            lines.add(new ExtractedInvoiceData.ExtractedLine(
                    "Réduction boutique",
                    BigDecimal.ONE,
                    ttcToHt(discountTtc).negate(),
                    DEFAULT_VAT_PERCENT));
        }

        BigDecimal shippingTtc = decimalOrNull(node, "shipping_fees");
        BigDecimal shippingHt = shippingTtc != null && shippingTtc.signum() > 0
                ? ttcToHt(shippingTtc) : null;

        BigDecimal totalTtc = decimalOrNull(node, "total_amount");

        return new ExtractedInvoiceData(
                null,
                textOrNull(node, "order_number"),
                textOrNull(node, "merchant"),
                textOrNull(node, "invoice_date"),
                customer,
                lines,
                shippingHt,
                shippingHt != null ? DEFAULT_VAT_PERCENT : null,
                null,
                null,
                totalTtc,
                textOrNull(node, "currency"));
    }

    private ExtractedInvoiceData parseStandardPayload(JsonNode node) {
        JsonNode customerNode = node.path("customer");
        ExtractedInvoiceData.ExtractedCustomer customer = new ExtractedInvoiceData.ExtractedCustomer(
                textOrNull(customerNode, "customerType"),
                textOrNull(customerNode, "companyName"),
                textOrNull(customerNode, "firstName"),
                textOrNull(customerNode, "lastName"),
                textOrNull(customerNode, "email"),
                textOrNull(customerNode, "phone"),
                textOrNull(customerNode, "addressLine1"),
                textOrNull(customerNode, "addressLine2"),
                textOrNull(customerNode, "postalCode"),
                textOrNull(customerNode, "city"),
                textOrNull(customerNode, "countryCode"));

        List<ExtractedInvoiceData.ExtractedLine> lines = new ArrayList<>();
        for (JsonNode lineNode : node.path("lines")) {
            lines.add(new ExtractedInvoiceData.ExtractedLine(
                    textOrNull(lineNode, "description"),
                    decimalOrZero(lineNode, "quantity"),
                    decimalOrZero(lineNode, "unitPriceHt"),
                    decimalOrDefault(lineNode, "vatRate", DEFAULT_VAT_PERCENT)));
        }

        return new ExtractedInvoiceData(
                textOrNull(node, "invoiceNumber"),
                textOrNull(node, "orderNumber"),
                textOrNull(node, "merchant"),
                textOrNull(node, "issueDate"),
                customer,
                lines,
                decimalOrNull(node, "shippingAmountHt"),
                decimalOrNull(node, "shippingVatRate"),
                decimalOrNull(node, "totalHt"),
                decimalOrNull(node, "totalVat"),
                decimalOrNull(node, "totalTtc"),
                textOrNull(node, "currency"));
    }

    private static BigDecimal ttcToHt(BigDecimal ttc) {
        if (ttc == null) {
            return BigDecimal.ZERO;
        }
        return ttc.divide(
                BigDecimal.ONE.add(DEFAULT_VAT_PERCENT.movePointLeft(2)),
                4,
                RoundingMode.HALF_UP);
    }

    private static String marketplaceItemDescription(JsonNode itemNode) {
        String base = textOrNull(itemNode, "description");
        if (base == null) {
            base = "";
        }
        String size = textOrNull(itemNode, "size");
        String color = textOrNull(itemNode, "color");
        StringBuilder sb = new StringBuilder(base.trim());
        if (size != null && !size.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(" — ");
            }
            sb.append(size.trim());
        }
        if (color != null && !color.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(" — ");
            }
            sb.append(color.trim());
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? "Article" : result;
    }

    private static NameParts splitFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return new NameParts(null, null);
        }
        String cleaned = fullName.replaceAll("\\([^)]*\\)", "").trim();
        int space = cleaned.indexOf(' ');
        if (space <= 0) {
            return new NameParts(cleaned, null);
        }
        return new NameParts(cleaned.substring(0, space).trim(), cleaned.substring(space + 1).trim());
    }

    private static String countryToIso(String country) {
        if (country == null || country.isBlank()) {
            return "FR";
        }
        if (country.length() == 2) {
            return country.toUpperCase(Locale.ROOT);
        }
        if ("france".equalsIgnoreCase(country.trim())) {
            return "FR";
        }
        return country.trim();
    }

    private record NameParts(String firstName, String lastName) {
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private static BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        return v.decimalValue();
    }

    private static BigDecimal decimalOrZero(JsonNode node, String field) {
        BigDecimal d = decimalOrNull(node, field);
        return d != null ? d : BigDecimal.ZERO;
    }

    private static BigDecimal decimalOrDefault(JsonNode node, String field, BigDecimal defaultValue) {
        BigDecimal d = decimalOrNull(node, field);
        return d != null ? d : defaultValue;
    }
}
