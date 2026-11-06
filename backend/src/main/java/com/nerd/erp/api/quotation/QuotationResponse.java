package com.nerd.erp.api.quotation;

import com.nerd.erp.api.document.VatLineResponse;
import com.nerd.erp.domain.quotation.QuotationDecisionSource;
import com.nerd.erp.domain.quotation.QuotationStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Construit via builder nommé ; voir {@code QuotationMapper}. */
@Builder
public record QuotationResponse(
        UUID id,
        UUID workspaceId,
        UUID customerId,
        /** Libellé du client au moment de la lecture (confort d'affichage des listes). */
        String customerName,
        String number,
        QuotationStatus status,
        LocalDate issueDate,
        LocalDate validUntil,
        /** Sous-total HT brut avant remise. */
        BigDecimal subtotalHt,
        BigDecimal discountPercent,
        BigDecimal discountAmountHt,
        /** Totaux nets après remise. */
        BigDecimal totalHt,
        BigDecimal totalVat,
        BigDecimal totalTtc,
        BigDecimal depositPercent,
        /** Acompte TTC = totalTtc × depositPercent / 100. */
        BigDecimal depositAmountTtc,
        /** Récapitulatif TVA par taux (net de remise). */
        List<VatLineResponse> vatBreakdown,
        String notes,
        UUID convertedInvoiceId,
        /** Facture d'acompte déjà émise sur ce devis (null sinon). */
        UUID depositInvoiceId,
        String depositInvoiceNumber,
        Instant sentAt,
        /** Lien public d'acceptation actif (jeton émis lors de l'envoi par e-mail). */
        boolean publicLinkActive,
        Instant decidedAt,
        String decidedBy,
        String decisionComment,
        QuotationDecisionSource decisionSource,
        List<QuotationLineResponse> lines
) {
}
