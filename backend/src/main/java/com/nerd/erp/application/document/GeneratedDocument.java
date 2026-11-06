package com.nerd.erp.application.document;

/** Document binaire prêt à être servi (PDF). */
public record GeneratedDocument(byte[] content, String filename) {

    static String sanitizeFilename(String value) {
        return (value == null ? "document" : value).replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
