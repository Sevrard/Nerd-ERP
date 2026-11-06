package com.nerd.erp.application.document;

import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.domain.workspace.SequenceReset;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Motif de numérotation des documents. Jetons : {@code {PREFIX}}, {@code {YYYY}}, {@code {YY}}, {@code {MM}},
 * {@code {SEQ}} (5 chiffres) ou {@code {SEQ:n}} (n chiffres, 1 à 9). Le motif par défaut
 * {@code {PREFIX}-{YYYY}-{SEQ:5}} reproduit la numérotation historique {@code FAC-2026-00042}.
 */
public final class NumberPattern {

    public static final String DEFAULT = "{PREFIX}-{YYYY}-{SEQ:5}";
    public static final String INVALID_PATTERN = "INVALID_NUMBER_PATTERN";

    private static final Pattern TOKEN = Pattern.compile("\\{(PREFIX|YYYY|YY|MM|SEQ(?::([1-9]))?)}");
    private static final Pattern ANY_BRACE = Pattern.compile("[{}]");

    private NumberPattern() {
    }

    public static String format(String pattern, String prefix, LocalDate date, long sequence) {
        String p = pattern == null || pattern.isBlank() ? DEFAULT : pattern;
        Matcher m = TOKEN.matcher(p);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String token = m.group(1);
            String value;
            if (token.startsWith("SEQ")) {
                int width = m.group(2) == null ? 5 : Integer.parseInt(m.group(2));
                value = String.format("%0" + width + "d", sequence);
            } else {
                value = switch (token) {
                    case "PREFIX" -> prefix == null ? "" : prefix;
                    case "YYYY" -> String.valueOf(date.getYear());
                    case "YY" -> String.format("%02d", date.getYear() % 100);
                    case "MM" -> String.format("%02d", date.getMonthValue());
                    default -> throw new IllegalStateException(token);
                };
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Vérifie la cohérence motif / périodicité : le motif doit contenir une séquence, ne contenir que des jetons
     * connus, et porter la période de remise à zéro (sinon deux documents de périodes différentes auraient le
     * même numéro).
     */
    public static void validate(String pattern, SequenceReset reset) {
        if (pattern == null || pattern.isBlank()) {
            return;
        }
        if (pattern.length() > 64) {
            throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_PATTERN, "Le motif de numérotation est trop long (64 max).");
        }
        String stripped = TOKEN.matcher(pattern).replaceAll("");
        if (ANY_BRACE.matcher(stripped).find()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_PATTERN,
                    "Jeton inconnu dans le motif. Jetons acceptés : {PREFIX}, {YYYY}, {YY}, {MM}, {SEQ}, {SEQ:n}.");
        }
        if (!pattern.contains("{SEQ")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_PATTERN, "Le motif doit contenir le compteur {SEQ}.");
        }
        SequenceReset r = reset == null ? SequenceReset.YEARLY : reset;
        boolean hasYear = pattern.contains("{YYYY}") || pattern.contains("{YY}");
        if (r == SequenceReset.YEARLY && !hasYear) {
            throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_PATTERN,
                    "Avec une remise à zéro annuelle, le motif doit contenir l'année ({YYYY} ou {YY}).");
        }
        if (r == SequenceReset.MONTHLY && !(hasYear && pattern.contains("{MM}"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_PATTERN,
                    "Avec une remise à zéro mensuelle, le motif doit contenir l'année et le mois ({MM}).");
        }
    }
}
