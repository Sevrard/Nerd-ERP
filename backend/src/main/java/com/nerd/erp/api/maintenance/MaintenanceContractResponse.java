package com.nerd.erp.api.maintenance;

import com.nerd.erp.domain.maintenance.MaintenanceContractStatus;
import com.nerd.erp.domain.maintenance.MaintenancePeriodicity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MaintenanceContractResponse(
        UUID id,
        UUID workspaceId,
        UUID customerId,
        UUID productId,
        String title,
        String description,
        MaintenancePeriodicity periodicity,
        BigDecimal amountHt,
        BigDecimal vatRate,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate nextRenewalDate,
        MaintenanceContractStatus status
) {
}
