package com.nerd.erp.application.importer;

import com.nerd.erp.api.error.ApiException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lecture d'un CSV « tableur » (export Excel / LibreOffice / Google Sheets) : BOM UTF-8 ignoré, séparateur
 * détecté ({@code ;} ou {@code ,} ou tabulation), guillemets RFC 4180, en-têtes normalisés (minuscules,
 * sans accents ni espaces) pour tolérer « Raison sociale », « raison_sociale », « RAISON SOCIALE »…
 */
public final class CsvTable {

    public static final String INVALID_CSV = "INVALID_CSV";
    public static final int MAX_ROWS = 5000;

    /** Ligne de données : numéro de ligne (1 = première ligne du fichier, en-tête incluse) + valeurs par en-tête. */
    public record Row(int lineNumber, Map<String, String> values) {
        /** Valeur « trim » pour l'un des alias d'en-tête, ou {@code null} si vide/absente. */
        public String get(String... headerAliases) {
            for (String alias : headerAliases) {
                String v = values.get(normalizeHeader(alias));
                if (v != null && !v.isBlank()) {
                    return v.trim();
                }
            }
            return null;
        }
    }

    private final List<String> headers;
    private final List<Row> rows;

    private CsvTable(List<String> headers, List<Row> rows) {
        this.headers = headers;
        this.rows = rows;
    }

    public List<String> headers() {
        return headers;
    }

    public List<Row> rows() {
        return rows;
    }

    public boolean hasHeader(String... aliases) {
        for (String alias : aliases) {
            if (headers.contains(normalizeHeader(alias))) {
                return true;
            }
        }
        return false;
    }

    public static CsvTable parse(byte[] content) {
        if (content == null || content.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_CSV, "Le fichier est vide.");
        }
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.indexOf('\uFFFD') >= 0) {
            // Fichier probablement en Latin-1 (Excel Windows) : on retente.
            text = new String(content, StandardCharsets.ISO_8859_1);
        }
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        char separator = detectSeparator(text);
        List<List<String>> records = split(text, separator);
        if (records.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_CSV, "Le fichier ne contient aucune ligne.");
        }
        List<String> headers = records.getFirst().stream().map(CsvTable::normalizeHeader).toList();
        if (headers.stream().allMatch(String::isBlank)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_CSV, "La première ligne doit contenir les en-têtes.");
        }
        if (records.size() - 1 > MAX_ROWS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_CSV,
                    "Trop de lignes (" + (records.size() - 1) + ") : " + MAX_ROWS + " maximum par import.");
        }
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> rec = records.get(i);
            if (rec.stream().allMatch(v -> v == null || v.isBlank())) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int c = 0; c < headers.size() && c < rec.size(); c++) {
                if (!headers.get(c).isBlank()) {
                    values.put(headers.get(c), rec.get(c));
                }
            }
            rows.add(new Row(i + 1, values));
        }
        return new CsvTable(headers, rows);
    }

    /** « Raison sociale » → « raisonsociale » ; « Prix_HT » → « prixht ». */
    public static String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        String ascii = Normalizer.normalize(header, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private static char detectSeparator(String text) {
        int end = text.indexOf('\n');
        String firstLine = end < 0 ? text : text.substring(0, end);
        long semi = firstLine.chars().filter(c -> c == ';').count();
        long comma = firstLine.chars().filter(c -> c == ',').count();
        long tab = firstLine.chars().filter(c -> c == '\t').count();
        if (tab > semi && tab > comma) {
            return '\t';
        }
        return semi >= comma ? ';' : ',';
    }

    private static List<List<String>> split(String text, char separator) {
        List<List<String>> records = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == separator) {
                current.add(field.toString());
                field.setLength(0);
            } else if (ch == '\r') {
                // ignoré (CRLF)
            } else if (ch == '\n') {
                current.add(field.toString());
                field.setLength(0);
                records.add(current);
                current = new ArrayList<>();
            } else {
                field.append(ch);
            }
        }
        if (!field.isEmpty() || !current.isEmpty()) {
            current.add(field.toString());
            records.add(current);
        }
        return records;
    }
}
