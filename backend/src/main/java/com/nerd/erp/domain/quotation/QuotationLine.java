package com.nerd.erp.domain.quotation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "quotation_line")
@Getter
@Setter
public class QuotationLine {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "line_order", nullable = false)
    private int lineOrder;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "unit_price_ht", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPriceHt;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Column(name = "line_total_ht", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineTotalHt;

    @Column(name = "line_total_vat", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineTotalVat;

    @Column(name = "line_total_ttc", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineTotalTtc;
}
