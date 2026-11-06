package com.nerd.erp.api.invoice;

import com.nerd.erp.api.quotation.DocumentLineRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Création d'un avoir sur une facture émise.
 *
 * @param reason motif (obligatoire, reporté sur le PDF)
 * @param lines  lignes à créditer (quantités positives) ; vide ou absent = avoir total reprenant
 *               toutes les lignes et la remise de la facture d'origine
 */
public record CreditNoteRequest(
        @NotBlank @Size(max = 512) String reason,
        @Valid List<DocumentLineRequest> lines
) {
    public boolean isTotal() {
        return lines == null || lines.isEmpty();
    }
}
