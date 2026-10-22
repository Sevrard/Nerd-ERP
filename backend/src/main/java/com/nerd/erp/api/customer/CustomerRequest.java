package com.nerd.erp.api.customer;

import com.nerd.erp.domain.customer.CustomerStatus;
import com.nerd.erp.domain.customer.CustomerType;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CustomerRequest(
        @NotNull UUID workspaceId,
        @NotNull CustomerType customerType,
        String companyName,
        String firstName,
        String lastName,
        String email,
        String phone,
        String addressLine1,
        String addressLine2,
        String postalCode,
        String city,
        String countryCode,
        CustomerStatus status,
        String notes,
        Instant nextFollowUpAt
) {
}
