package com.nerd.erp.domain.invoice;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "invoice")
@Getter
@Setter
public class Invoice extends TenantScopedEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "source_quotation_id")
    private UUID sourceQuotationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", nullable = false, length = 20)
    private InvoiceType invoiceType = InvoiceType.STANDARD;

    @Column(nullable = false, length = 64)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "total_ht", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalHt = BigDecimal.ZERO;

    @Column(name = "total_vat", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalVat = BigDecimal.ZERO;

    @Column(name = "total_ttc", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalTtc = BigDecimal.ZERO;

    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    /** Remise commerciale globale en % du HT (reportée depuis le devis). */
    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    /** Montant HT de la remise (sous-total brut = totalHt + discountAmountHt). */
    @Column(name = "discount_amount_ht", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmountHt = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_customer", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> snapshotCustomer = Map.of();

    private String notes;

    @Column(name = "external_reference", length = 128)
    private String externalReference;

    @Column(length = 64)
    private String merchant;

    @Column(name = "order_number", length = 128)
    private String orderNumber;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "source_document_path", length = 512)
    private String sourceDocumentPath;

    /** Pour un avoir : facture d'origine. */
    @Column(name = "credited_invoice_id")
    private UUID creditedInvoiceId;

    @Column(name = "credit_reason", length = 512)
    private String creditReason;

    /** Facture de solde : facture d'acompte déduite. */
    @Column(name = "deposit_invoice_id")
    private UUID depositInvoiceId;

    /** Dernier envoi par e-mail au client. */
    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "reminder_count", nullable = false)
    private int reminderCount = 0;

    @Column(name = "last_reminder_at")
    private Instant lastReminderAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder ASC")
    private List<InvoiceLine> lines = new ArrayList<>();

    public boolean isMutableDraft() {
        return status == InvoiceStatus.DRAFT;
    }

    public boolean isCreditNote() {
        return invoiceType == InvoiceType.CREDIT_NOTE;
    }

    public boolean isDeposit() {
        return invoiceType == InvoiceType.DEPOSIT;
    }

    /** Reste à payer (TTC - encaissé), jamais négatif. */
    public BigDecimal remainingAmount() {
        BigDecimal paid = amountPaid == null ? BigDecimal.ZERO : amountPaid;
        BigDecimal ttc = totalTtc == null ? BigDecimal.ZERO : totalTtc;
        return ttc.subtract(paid).max(BigDecimal.ZERO);
    }

    public void replaceLines(List<InvoiceLine> newLines) {
        lines.clear();
        for (InvoiceLine line : newLines) {
            line.setInvoice(this);
            lines.add(line);
        }
    }
}
