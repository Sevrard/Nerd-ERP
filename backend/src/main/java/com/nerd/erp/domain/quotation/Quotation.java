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

    private String notes;

    @Column(name = "converted_invoice_id")
    private UUID convertedInvoiceId;

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
