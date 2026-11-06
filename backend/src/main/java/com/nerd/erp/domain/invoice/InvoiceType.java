package com.nerd.erp.domain.invoice;

public enum InvoiceType {
    STANDARD,
    /** Facture d'acompte émise sur un devis accepté, déduite de la facture de solde. */
    DEPOSIT,
    CREDIT_NOTE
}
