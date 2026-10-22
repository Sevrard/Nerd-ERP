package com.nerd.erp.api.maintenance;

import com.nerd.erp.domain.maintenance.MaintenanceContractStatus;
import com.nerd.erp.domain.maintenance.MaintenancePeriodicity;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MaintenanceContractRequest(
        @NotNull UUID workspaceId,
        @NotNull UUID customerId,
        UUID productId,
        @NotBlank String title,
        String description,
        MaintenancePeriodicity periodicity,
        @NotNull @DecimalMin("0.0") BigDecimal amountHt,
        BigDecimal vatRate,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        LocalDate nextRenewalDate,
        MaintenanceContractStatus status
) {
}
