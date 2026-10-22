package com.nerd.erp.application.workspace;

import java.text.Normalizer;
import java.util.Locale;

final class WorkspaceSlugHelper {

    private WorkspaceSlugHelper() {
    }

    static String slugFromName(String name) {
        return normalize(name);
    }

    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "workspace";
        }
        String n = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        if (n.isEmpty()) {
            return "workspace";
        }
        return n.length() > 100 ? n.substring(0, 100).replaceAll("-$", "") : n;
    }
}
