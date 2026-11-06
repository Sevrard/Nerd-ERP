package com.nerd.erp.domain.quotation;

/** Origine de la décision (acceptation / refus) portée sur un devis. */
public enum QuotationDecisionSource {
    /** Saisie par un utilisateur du workspace. */
    MANUAL,
    /** Décision du client via le lien public signé. */
    ONLINE
}
