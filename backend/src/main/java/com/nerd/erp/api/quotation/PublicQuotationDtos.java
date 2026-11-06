package com.nerd.erp.api.quotation;

import com.nerd.erp.api.document.VatLineResponse;
import com.nerd.erp.domain.quotation.QuotationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Échanges du parcours public d'acceptation d'un devis (sans session). */
public final class PublicQuotationDtos {

    private PublicQuotationDtos() {
    }

    /** Vue client du devis : pas d'identifiants internes, uniquement ce qui figure sur le PDF. */
    public record PublicQuotationView(
            String number,
            QuotationStatus status,
            /** Le client peut encore accepter / refuser (statut SENT et validité non dépassée). */
            boolean decidable,
            boolean expired,
            LocalDate issueDate,
            LocalDate validUntil,
            String issuerName,
            String issuerEmail,
            String customerName,
            String currencyCode,
            BigDecimal subtotalHt,
            BigDecimal discountPercent,
            BigDecimal discountAmountHt,
            BigDecimal totalHt,
            BigDecimal totalVat,
            BigDecimal totalTtc,
            BigDecimal depositPercent,
            BigDecimal depositAmountTtc,
            List<VatLineResponse> vatBreakdown,
            String notes,
            Instant decidedAt,
            String decidedBy,
            List<PublicLine> lines
    ) {
    }

    public record PublicLine(String description, BigDecimal quantity, BigDecimal unitPriceHt, BigDecimal vatRate,
                             BigDecimal discountPercent, BigDecimal lineTotalHt) {
    }

    public record AcceptRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 1024) String comment
    ) {
    }

    public record RejectRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 1024) String reason
    ) {
    }
}
