package com.nerd.erp.application.dashboard;

import com.nerd.erp.domain.customer.CustomerStatus;
import com.nerd.erp.infrastructure.persistence.customer.CustomerRepository;
import com.nerd.erp.infrastructure.persistence.customer.FollowUpRepository;
import com.nerd.erp.api.dashboard.DashboardSummaryResponse;
import com.nerd.erp.api.dashboard.MonthlyRevenuePoint;
import com.nerd.erp.api.dashboard.ProductSalesStat;
import com.nerd.erp.api.dashboard.RevenueTrendResponse;
import com.nerd.erp.infrastructure.persistence.invoice.InvoiceLineRepository;
import com.nerd.erp.infrastructure.persistence.invoice.InvoiceRepository;
import com.nerd.erp.infrastructure.persistence.invoice.projection.ProductSalesRow;
import com.nerd.erp.infrastructure.persistence.maintenance.MaintenanceContractRepository;
import com.nerd.erp.infrastructure.persistence.payment.PaymentRepository;
import com.nerd.erp.domain.quotation.QuotationStatus;
import com.nerd.erp.infrastructure.persistence.quotation.QuotationRepository;
import com.nerd.erp.infrastructure.security.core.SecurityContextHelper;
import com.nerd.erp.application.workspace.WorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DashboardService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final QuotationRepository quotationRepository;
    private final MaintenanceContractRepository maintenanceContractRepository;
    private final FollowUpRepository followUpRepository;
    private final WorkspaceService workspaceService;

    public DashboardService(InvoiceRepository invoiceRepository,
                            InvoiceLineRepository invoiceLineRepository,
                            PaymentRepository paymentRepository,
                            CustomerRepository customerRepository,
                            QuotationRepository quotationRepository,
                            MaintenanceContractRepository maintenanceContractRepository,
                            FollowUpRepository followUpRepository,
                            WorkspaceService workspaceService) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.paymentRepository = paymentRepository;
        this.customerRepository = customerRepository;
        this.quotationRepository = quotationRepository;
        this.maintenanceContractRepository = maintenanceContractRepository;
        this.followUpRepository = followUpRepository;
        this.workspaceService = workspaceService;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(UUID workspaceId, LocalDate from, LocalDate to, boolean allPeriods) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        if (workspaceId != null) {
            workspaceService.requireInTenant(workspaceId, tenantId);
        }

        BigDecimal invoiced;
        BigDecimal collected;
        long invoiceCount;
        BigDecimal unitsSold = BigDecimal.ZERO;
        if (allPeriods) {
            invoiced = invoiceRepository.sumInvoicedRevenueAllTime(tenantId, workspaceId);
            collected = paymentRepository.sumCollectedAllTime(tenantId, workspaceId)
                    .add(invoiceRepository.sumDirectAmountPaidAllTime(tenantId, workspaceId));
            invoiceCount = invoiceRepository.countInvoicedInvoicesAllTime(tenantId, workspaceId);
            if (workspaceId != null) {
                unitsSold = invoiceLineRepository.sumUnitsSoldAllTime(tenantId, workspaceId);
            }
        } else {
            LocalDate periodFrom = from != null ? from : LocalDate.now().minusMonths(12);
            LocalDate periodTo = to != null ? to : LocalDate.now();
            invoiced = invoiceRepository.sumInvoicedRevenue(tenantId, workspaceId, periodFrom, periodTo);
            collected = paymentRepository.sumCollected(tenantId, workspaceId, periodFrom, periodTo)
                    .add(invoiceRepository.sumDirectAmountPaidByIssueDate(tenantId, workspaceId, periodFrom, periodTo));
            invoiceCount = invoiceRepository.countInvoicedInvoices(tenantId, workspaceId, periodFrom, periodTo);
            if (workspaceId != null) {
                unitsSold = invoiceLineRepository.sumUnitsSoldInPeriod(tenantId, workspaceId, periodFrom, periodTo);
            }
        }
        BigDecimal outstanding = invoiceRepository.sumOutstanding(tenantId, workspaceId);
        long overdue = invoiceRepository.countOverdue(tenantId, workspaceId, LocalDate.now());

        long prospects = workspaceId == null ? 0 :
                customerRepository.countByTenantIdAndWorkspaceIdAndStatusAndArchivedFalse(
                        tenantId, workspaceId, CustomerStatus.PROSPECT);
        long clients = workspaceId == null ? 0 :
                customerRepository.countByTenantIdAndWorkspaceIdAndStatusAndArchivedFalse(
                        tenantId, workspaceId, CustomerStatus.CLIENT);

        long pendingQuotes = workspaceId == null ? 0 :
                quotationRepository.countByTenantIdAndWorkspaceIdAndStatus(tenantId, workspaceId, QuotationStatus.SENT);

        LocalDate renewalHorizon = LocalDate.now().plusDays(30);
        long renewals = maintenanceContractRepository.countRenewalsDue(tenantId, workspaceId, renewalHorizon);

        return new DashboardSummaryResponse(
                invoiced, collected, outstanding, overdue, prospects, clients, pendingQuotes, renewals,
                invoiceCount, unitsSold);
    }

    @Transactional(readOnly = true)
    public RevenueTrendResponse revenueTrend(UUID workspaceId,
                                             int months,
                                             LocalDate from,
                                             LocalDate to,
                                             boolean allPeriods) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        if (workspaceId != null) {
            workspaceService.requireInTenant(workspaceId, tenantId);
        }

        YearMonth rangeEnd;
        YearMonth rangeStart;
        if (allPeriods) {
            int span = Math.min(Math.max(months, 1), 24);
            rangeEnd = YearMonth.from(LocalDate.now());
            rangeStart = rangeEnd.minusMonths(span - 1);
        } else {
            LocalDate periodFrom = from != null ? from : LocalDate.now().minusMonths(months - 1L);
            LocalDate periodTo = to != null ? to : LocalDate.now();
            if (periodTo.isBefore(periodFrom)) {
                LocalDate swap = periodFrom;
                periodFrom = periodTo;
                periodTo = swap;
            }
            rangeStart = YearMonth.from(periodFrom);
            rangeEnd = YearMonth.from(periodTo);
            long monthSpan = rangeStart.until(rangeEnd, ChronoUnit.MONTHS) + 1;
            if (monthSpan > 24) {
                rangeStart = rangeEnd.minusMonths(23);
            }
        }

        List<MonthlyRevenuePoint> points = new ArrayList<>();
        Locale fr = Locale.FRENCH;
        for (YearMonth ym = rangeStart; !ym.isAfter(rangeEnd); ym = ym.plusMonths(1)) {
            LocalDate monthFrom = ym.atDay(1);
            LocalDate monthTo = ym.atEndOfMonth();
            BigDecimal invoiced = invoiceRepository.sumInvoicedRevenue(tenantId, workspaceId, monthFrom, monthTo);
            BigDecimal collected = paymentRepository.sumCollected(tenantId, workspaceId, monthFrom, monthTo)
                    .add(invoiceRepository.sumDirectAmountPaidByIssueDate(tenantId, workspaceId, monthFrom, monthTo));
            String label = ym.getMonth().getDisplayName(TextStyle.SHORT, fr);
            if (!label.isEmpty()) {
                label = Character.toUpperCase(label.charAt(0)) + label.substring(1);
            }
            points.add(new MonthlyRevenuePoint(ym.toString(), label, invoiced, collected));
        }
        return new RevenueTrendResponse(points);
    }

    @Transactional(readOnly = true)
    public List<ProductSalesStat> productSales(UUID workspaceId, LocalDate from, LocalDate to, boolean allPeriods) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        if (workspaceId == null) {
            return List.of();
        }
        workspaceService.requireInTenant(workspaceId, tenantId);

        List<ProductSalesRow> rows;
        if (allPeriods) {
            rows = invoiceLineRepository.aggregateSalesByProductAllTime(tenantId, workspaceId);
        } else {
            LocalDate periodFrom = from != null ? from : LocalDate.now().minusMonths(12);
            LocalDate periodTo = to != null ? to : LocalDate.now();
            rows = invoiceLineRepository.aggregateSalesByProductInPeriod(tenantId, workspaceId, periodFrom, periodTo);
        }

        return rows.stream().map(this::toProductSalesStat).toList();
    }

    private ProductSalesStat toProductSalesStat(ProductSalesRow row) {
        return new ProductSalesStat(
                row.getProductId(),
                row.getProductName(),
                row.getProductReferenceCode(),
                row.getQuantitySold(),
                row.getRevenueHt(),
                row.getRevenueTtc(),
                row.getOrderCount() != null ? row.getOrderCount() : 0L,
                row.getLineCount() != null ? row.getLineCount() : 0L);
    }

    @Transactional(readOnly = true)
    public Object upcomingActions(UUID workspaceId, int days) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        if (workspaceId != null) {
            workspaceService.requireInTenant(workspaceId, tenantId);
        }
        Instant until = Instant.now().plus(days, ChronoUnit.DAYS);
        return followUpRepository.findUpcoming(tenantId, workspaceId, until);
    }
}
