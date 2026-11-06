package com.nerd.erp.api.invoice;

import com.nerd.erp.api.document.VatLineResponse;
import com.nerd.erp.domain.invoice.InvoiceStatus;
import com.nerd.erp.domain.invoice.InvoiceType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Construit via builder nommé ; voir {@code InvoiceMapper}. */
@Builder
public record InvoiceResponse(
        UUID id,
        UUID workspaceId,
        UUID customerId,
        UUID sourceQuotationId,
        InvoiceType invoiceType,
        String number,
        InvoiceStatus status,
        LocalDate issueDate,
        LocalDate dueDate,
        /** Sous-total HT brut avant remise. */
        BigDecimal subtotalHt,
        BigDecimal discountPercent,
        BigDecimal discountAmountHt,
        BigDecimal totalHt,
        BigDecimal totalVat,
        BigDecimal totalTtc,
        BigDecimal amountPaid,
        Map<String, Object> snapshotCustomer,
        String notes,
        String externalReference,
        String merchant,
        String orderNumber,
        Instant issuedAt,
        /** Avoir : facture d'origine. */
        UUID creditedInvoiceId,
        String creditedInvoiceNumber,
        String creditReason,
        /** Facture : avoirs émis dessus. */
        List<LinkedDocument> creditNotes,
        /** Facture de solde : acompte déduit. Facture d'acompte : facture de solde émise ensuite. */
        LinkedDocument depositInvoice,
        LinkedDocument balanceInvoice,
        /** Récapitulatif TVA par taux (net de remise). */
        List<VatLineResponse> vatBreakdown,
        Instant sentAt,
        int reminderCount,
        Instant lastReminderAt,
        List<InvoiceLineResponse> lines
) {
    public record LinkedDocument(UUID id, String number, BigDecimal totalTtc) {
    }
}
