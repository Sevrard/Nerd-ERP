package com.nerd.erp.domain.catalog;

/** Mode de tarification d'un produit / prestation du catalogue. */
public enum BillingType {
    /** Vendu à l'unité (produit physique, article). */
    UNIT("unité"),
    /** Régie facturée à la journée (TJM). */
    DAYS("jour"),
    /** Forfait fixe. */
    FIXED_PRICE("forfait"),
    /** Abonnement / récurrent. */
    RECURRING("mois");

    private final String defaultUnitLabel;

    BillingType(String defaultUnitLabel) {
        this.defaultUnitLabel = defaultUnitLabel;
    }

    public String defaultUnitLabel() {
        return defaultUnitLabel;
    }
}
