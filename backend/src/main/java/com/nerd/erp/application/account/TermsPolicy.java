package com.nerd.erp.application.account;

/**
 * Version courante des conditions d'utilisation. À incrémenter lorsqu'une nouvelle version doit être
 * ré-acceptée par tous les utilisateurs (le frontend affiche alors la modale d'acceptation).
 */
public final class TermsPolicy {

    public static final String CURRENT_VERSION = "2026-01";

    private TermsPolicy() {
    }

    public static boolean accepted(String acceptedVersion) {
        return CURRENT_VERSION.equals(acceptedVersion);
    }
}
