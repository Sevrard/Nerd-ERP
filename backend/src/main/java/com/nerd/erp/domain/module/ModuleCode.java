package com.nerd.erp.domain.module;

/**
 * Modules optionnels activables par tenant. Le socle (clients, catalogue, devis, factures,
 * paiements, tableau de bord) est toujours disponible et n'apparaît pas ici.
 *
 * <p>Pour ajouter un module métier : une valeur ici, {@code @RequireModule} sur ses contrôleurs,
 * et la route / l'entrée de menu correspondante côté frontend.</p>
 */
public enum ModuleCode {
    MAINTENANCE("Contrats de maintenance", "Contrats récurrents, renouvellements et suivi des échéances."),
    INVOICE_SCAN("Import de factures PDF", "Extraction automatique (IA) des factures PDF et rapprochement.");

    private final String label;
    private final String description;

    ModuleCode(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }
}
