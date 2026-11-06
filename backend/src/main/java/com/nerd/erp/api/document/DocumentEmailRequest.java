package com.nerd.erp.api.document;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Envoi d'un devis ou d'une facture par e-mail (PDF joint).
 *
 * @param to      destinataire ; vide = e-mail du client
 * @param cc      copie optionnelle
 * @param message message personnel inséré dans le corps (texte brut)
 */
public record DocumentEmailRequest(
        @Email @Size(max = 255) String to,
        @Email @Size(max = 255) String cc,
        @Size(max = 2000) String message
) {
}
