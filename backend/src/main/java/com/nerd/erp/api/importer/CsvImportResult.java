package com.nerd.erp.api.importer;

import java.util.List;

/**
 * Bilan d'un import CSV. En mode {@code dryRun}, aucune écriture n'a eu lieu : les compteurs indiquent ce qui
 * <em>serait</em> fait.
 */
public record CsvImportResult(
        boolean dryRun,
        int totalRows,
        int created,
        int updated,
        int skipped,
        List<RowError> errors,
        /** Aperçu des premières lignes interprétées (libellé + action). */
        List<RowPreview> preview
) {
    public record RowError(int line, String message) {
    }

    public record RowPreview(int line, String label, String action) {
    }
}
