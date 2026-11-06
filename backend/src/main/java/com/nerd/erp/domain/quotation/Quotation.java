package com.nerd.erp.domain.quotation;

import com.nerd.erp.domain.shared.TenantScopedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quotation")
@Getter
@Setter
public class Quotation extends TenantScopedEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(length = 64)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuotationStatus status = QuotationStatus.DRAFT;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "total_ht", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalHt = BigDecimal.ZERO;

    @Column(name = "total_vat", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalVat = BigDecimal.ZERO;

    @Column(name = "total_ttc", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalTtc = BigDecimal.ZERO;

    /** Remise commerciale globale en % du HT (0 = aucune). */
    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    /** Montant HT de la remise (sous-total brut = totalHt + discountAmountHt). */
    @Column(name = "discount_amount_ht", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmountHt = BigDecimal.ZERO;

    /** Acompte exigé à la commande en % du TTC. */
    @Column(name = "deposit_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal depositPercent = BigDecimal.ZERO;

    private String notes;

    @Column(name = "converted_invoice_id")
    private UUID convertedInvoiceId;

    /** Dernier envoi par e-mail au client. */
    @Column(name = "sent_at")
    private Instant sentAt;

    /** SHA-256 du jeton du lien public d'acceptation (jamais le jeton brut). */
    @Column(name = "public_token_hash", length = 64)
    private String publicTokenHash;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** Nom saisi par le client (en ligne) ou de l'utilisateur ayant saisi la décision. */
    @Column(name = "decided_by", length = 255)
    private String decidedBy;

    @Column(name = "decision_comment", length = 1024)
    private String decisionComment;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_source", length = 20)
    private QuotationDecisionSource decisionSource;

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder ASC")
    private List<QuotationLine> lines = new ArrayList<>();

    public void replaceLines(List<QuotationLine> newLines) {
        lines.clear();
        for (QuotationLine line : newLines) {
            line.setQuotation(this);
            lines.add(line);
        }
    }
}
