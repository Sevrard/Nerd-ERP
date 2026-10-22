package com.nerd.erp.application.invoicescan;

import com.nerd.erp.domain.customer.Customer;
import com.nerd.erp.infrastructure.persistence.customer.CustomerRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class CustomerMatcher {

    private final CustomerRepository customerRepository;

    public CustomerMatcher(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public record MatchResult(CustomerMatchStatus status, UUID customerId) {
    }

    public MatchResult match(UUID tenantId, UUID workspaceId, ExtractedInvoiceData.ExtractedCustomer extracted) {
        if (extracted == null) {
            return new MatchResult(CustomerMatchStatus.NOT_FOUND, null);
        }
        if (extracted.email() != null && !extracted.email().isBlank()) {
            Optional<Customer> byEmail = customerRepository
                    .findByTenantIdAndWorkspaceIdAndArchivedFalseAndEmailIgnoreCase(
                            tenantId, workspaceId, extracted.email().trim());
            if (byEmail.isPresent()) {
                return new MatchResult(CustomerMatchStatus.FOUND, byEmail.get().getId());
            }
        }
        if (extracted.companyName() != null && !extracted.companyName().isBlank()) {
            List<Customer> byCompany = customerRepository
                    .findByTenantIdAndWorkspaceIdAndArchivedFalseAndCompanyNameIgnoreCase(
                            tenantId, workspaceId, extracted.companyName().trim());
            if (byCompany.size() == 1) {
                return new MatchResult(CustomerMatchStatus.FOUND, byCompany.get(0).getId());
            }
        }
        String first = extracted.firstName() != null ? extracted.firstName().trim() : "";
        String last = extracted.lastName() != null ? extracted.lastName().trim() : "";
        if (!first.isBlank() || !last.isBlank()) {
            List<Customer> byName = customerRepository
                    .findByTenantIdAndWorkspaceIdAndArchivedFalseAndFirstNameIgnoreCaseAndLastNameIgnoreCase(
                            tenantId, workspaceId, first, last);
            if (byName.size() == 1) {
                return new MatchResult(CustomerMatchStatus.FOUND, byName.get(0).getId());
            }
        }
        return new MatchResult(CustomerMatchStatus.NOT_FOUND, null);
    }
}
