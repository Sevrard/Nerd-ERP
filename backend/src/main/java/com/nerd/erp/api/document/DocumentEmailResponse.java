package com.nerd.erp.api.document;

import java.time.Instant;

/** Résultat d'un envoi : destinataire effectif et horodatage. */
public record DocumentEmailResponse(String to, Instant sentAt) {
}
