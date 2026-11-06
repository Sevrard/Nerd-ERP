package com.nerd.erp.domain.account;

import java.time.Duration;

/** Jetons à usage unique envoyés par e-mail. */
public enum AccountTokenKind {
    /** Activation d'un compte invité (choix du mot de passe). */
    INVITATION(Duration.ofDays(7)),
    /** Réinitialisation d'un mot de passe oublié. */
    PASSWORD_RESET(Duration.ofHours(1));

    private final Duration validity;

    AccountTokenKind(Duration validity) {
        this.validity = validity;
    }

    public Duration validity() {
        return validity;
    }
}
