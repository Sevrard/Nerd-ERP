package com.nerd.erp.api.dashboard;

import java.math.BigDecimal;

public record DashboardSummaryResponse(
        BigDecimal invoicedRevenue,
        BigDecimal collectedAmount,
        BigDecimal outstandingAmount,
        long overdueInvoices,
        long activeProspects,
        long activeClients,
        long pendingQuotations,
        long maintenanceRenewalsDue,
        long invoiceCountInPeriod,
        BigDecimal unitsSold
) {
}
