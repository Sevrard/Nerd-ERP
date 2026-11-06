package com.nerd.erp.domain.mail;

/** Mode de sécurisation de la connexion SMTP. */
public enum SmtpSecurity {
    /** Port 587 en général : connexion en clair puis STARTTLS obligatoire. */
    STARTTLS,
    /** Port 465 : TLS implicite dès la connexion. */
    SSL,
    /** Aucun chiffrement (serveur local de test type Mailpit uniquement). */
    NONE
}
