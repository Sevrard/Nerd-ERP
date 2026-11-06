package com.nerd.erp.domain.mail;

/** Nature d'un e-mail, pour le journal et le choix de l'expéditeur. */
public enum EmailKind {
    /** E-mail de test depuis l'écran de configuration SMTP. */
    TEST,
    /** Invitation, mot de passe, notifications de compte : expéditeur plateforme. */
    ACCOUNT,
    /** Devis envoyé à un client final. */
    QUOTATION,
    /** Facture ou avoir envoyé à un client final. */
    INVOICE,
    /** Relance de facture échue. */
    REMINDER
}
