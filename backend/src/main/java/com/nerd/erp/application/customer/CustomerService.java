package com.nerd.erp.application.customer;

import com.nerd.erp.domain.customer.Customer;
import com.nerd.erp.domain.customer.CustomerStatus;
import com.nerd.erp.domain.customer.CustomerType;
import com.nerd.erp.domain.customer.FollowUp;
import com.nerd.erp.api.customer.CustomerRequest;
import com.nerd.erp.api.customer.CustomerResponse;
import com.nerd.erp.api.customer.FollowUpRequest;
import com.nerd.erp.infrastructure.persistence.customer.CustomerRepository;
import com.nerd.erp.infrastructure.persistence.customer.FollowUpRepository;
import com.nerd.erp.infrastructure.persistence.invoice.InvoiceRepository;
import com.nerd.erp.infrastructure.persistence.maintenance.MaintenanceContractRepository;
import com.nerd.erp.infrastructure.persistence.payment.PaymentRepository;
import com.nerd.erp.infrastructure.persistence.quotation.QuotationRepository;
import com.nerd.erp.infrastructure.security.core.SecurityContextHelper;
import com.nerd.erp.application.audit.AuditService;
import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.api.shared.PageResponse;
import com.nerd.erp.application.workspace.WorkspaceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final FollowUpRepository followUpRepository;
    private final QuotationRepository quotationRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final MaintenanceContractRepository maintenanceContractRepository;
    private final WorkspaceService workspaceService;
    private final AuditService auditService;

    public CustomerService(CustomerRepository customerRepository,
                           FollowUpRepository followUpRepository,
                           QuotationRepository quotationRepository,
                           InvoiceRepository invoiceRepository,
                           PaymentRepository paymentRepository,
                           MaintenanceContractRepository maintenanceContractRepository,
                           WorkspaceService workspaceService,
                           AuditService auditService) {
        this.customerRepository = customerRepository;
        this.followUpRepository = followUpRepository;
        this.quotationRepository = quotationRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.maintenanceContractRepository = maintenanceContractRepository;
        this.workspaceService = workspaceService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> search(UUID workspaceId, CustomerStatus status, String q, Pageable pageable) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        workspaceService.requireInTenant(workspaceId, tenantId);
        String term = blankToNull(q);
        Page<Customer> page = term == null
                ? customerRepository.listByWorkspace(tenantId, workspaceId, status, pageable)
                : customerRepository.searchByWorkspace(tenantId, workspaceId, status, likePattern(term), pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(UUID id) {
        return toResponse(requireCustomer(id));
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        return toResponse(createAndReturnEntity(request));
    }

    /**
     * Complète l'e-mail des clients déjà liés à une facture Etsy importée (sync précédente sans email_r).
     */
    @Transactional
    public int backfillEmailsFromEtsyReceipts(UUID workspaceId, List<com.nerd.erp.api.etsysync.EtsySyncReceiptDraft> receipts) {
        SecurityContextHelper.requireWriteAccess();
        UUID tenantId = SecurityContextHelper.requireTenantId();
        workspaceService.requireInTenant(workspaceId, tenantId);
        int updated = 0;
        for (var draft : receipts) {
            String email = blankToNull(draft.buyerEmail() != null ? draft.buyerEmail().trim() : null);
            if (email == null) {
                continue;
            }
            var invoice = invoiceRepository.findByTenantIdAndWorkspaceIdAndEtsyReceiptId(
                    tenantId, workspaceId, draft.receiptId());
            if (invoice.isEmpty()) {
                continue;
            }
            Customer customer = requireCustomer(invoice.get().getCustomerId());
            if (blankToNull(customer.getEmail()) != null) {
                continue;
            }
            customer.setEmail(email);
            customerRepository.save(customer);
            updated++;
        }
        return updated;
    }

    /**
     * Client Etsy : recherche par e-mail, sinon par nom ; met à jour l'adresse si le client existe déjà.
     */
    @Transactional
    public Customer resolveFromEtsyImport(
            UUID workspaceId,
            String buyerName,
            String email,
            String addressLine1,
            String addressLine2,
            String postalCode,
            String city,
            String countryCode) {
        SecurityContextHelper.requireWriteAccess();
        UUID tenantId = SecurityContextHelper.requireTenantId();
        workspaceService.requireInTenant(workspaceId, tenantId);

        String trimmedEmail = blankToNull(email != null ? email.trim() : null);
        NameParts name = splitBuyerName(buyerName);
        String country = normalizeCountry(countryCode);

        if (trimmedEmail != null) {
            Optional<Customer> byEmail = customerRepository
                    .findByTenantIdAndWorkspaceIdAndArchivedFalseAndEmailIgnoreCase(
                            tenantId, workspaceId, trimmedEmail);
            if (byEmail.isPresent()) {
                Customer existing = byEmail.get();
                applyEtsyShippingAddress(existing, addressLine1, addressLine2, postalCode, city, country);
                mergeNameIfMissing(existing, name.firstName(), name.lastName());
                return customerRepository.save(existing);
            }
        }

        List<Customer> byName = customerRepository
                .findByTenantIdAndWorkspaceIdAndArchivedFalseAndFirstNameIgnoreCaseAndLastNameIgnoreCase(
                        tenantId, workspaceId, name.firstName(), name.lastName());
        if (!byName.isEmpty()) {
            Customer existing = byName.get(0);
            if (trimmedEmail != null && blankToNull(existing.getEmail()) == null) {
                existing.setEmail(trimmedEmail);
            }
            applyEtsyShippingAddress(existing, addressLine1, addressLine2, postalCode, city, country);
            return customerRepository.save(existing);
        }

        return createAndReturnEntity(new CustomerRequest(
                workspaceId,
                CustomerType.INDIVIDUAL,
                null,
                name.firstName(),
                name.lastName(),
                trimmedEmail,
                null,
                blankToNull(addressLine1),
                blankToNull(addressLine2),
                blankToNull(postalCode),
                blankToNull(city),
                country,
                CustomerStatus.CLIENT,
                "Client Etsy",
                null));
    }

    /**
     * Évite les doublons type « Aude ARCHAMBAULT » / « Aude Archambault » (comparaison insensible à la casse).
     */
    @Transactional
    public Customer findOrCreateIndividualByName(UUID workspaceId, String firstName, String lastName, String notes) {
        SecurityContextHelper.requireWriteAccess();
        UUID tenantId = SecurityContextHelper.requireTenantId();
        workspaceService.requireInTenant(workspaceId, tenantId);
        String fn = firstName != null ? firstName.trim() : "";
        String ln = lastName != null ? lastName.trim() : "";
        List<Customer> matches = customerRepository
                .findByTenantIdAndWorkspaceIdAndArchivedFalseAndFirstNameIgnoreCaseAndLastNameIgnoreCase(
                        tenantId, workspaceId, fn, ln);
        if (!matches.isEmpty()) {
            return matches.get(0);
        }
        return createAndReturnEntity(new CustomerRequest(
                workspaceId, CustomerType.INDIVIDUAL, null, fn, ln, null, null, null, null, null, null,
                "FR", CustomerStatus.CLIENT, notes, null));
    }

    @Transactional
    public Customer createAndReturnEntity(CustomerRequest request) {
        SecurityContextHelper.requireWriteAccess();
        UUID tenantId = SecurityContextHelper.requireTenantId();
        workspaceService.requireInTenant(request.workspaceId(), tenantId);
        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        applyRequest(customer, request);
        customer = customerRepository.save(customer);
        auditService.record(tenantId, "Customer", customer.getId(), "CREATE", Map.of());
        return customer;
    }

    @Transactional
    public CustomerResponse update(UUID id, CustomerRequest request) {
        SecurityContextHelper.requireWriteAccess();
        Customer customer = requireCustomer(id);
        if (!customer.getWorkspaceId().equals(request.workspaceId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot move customer to another workspace");
        }
        applyRequest(customer, request);
        customer = customerRepository.save(customer);
        auditService.record(customer.getTenantId(), "Customer", customer.getId(), "UPDATE", Map.of());
        return toResponse(customer);
    }

    @Transactional
    public CustomerResponse archive(UUID id) {
        SecurityContextHelper.requireWriteAccess();
        Customer customer = requireCustomer(id);
        customer.setArchived(true);
        customer.setStatus(CustomerStatus.INACTIVE);
        customer = customerRepository.save(customer);
        auditService.record(customer.getTenantId(), "Customer", customer.getId(), "ARCHIVE", Map.of());
        return toResponse(customer);
    }

    @Transactional
    public void delete(UUID id) {
        SecurityContextHelper.requireWriteAccess();
        Customer customer = requireCustomer(id);
        UUID tenantId = customer.getTenantId();
        if (quotationRepository.existsByTenantIdAndCustomerId(tenantId, id)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Impossible de supprimer : client lié à un ou plusieurs devis");
        }
        if (invoiceRepository.existsByTenantIdAndCustomerId(tenantId, id)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Impossible de supprimer : client lié à une ou plusieurs factures");
        }
        if (paymentRepository.existsByTenantIdAndCustomerId(tenantId, id)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Impossible de supprimer : client lié à un ou plusieurs encaissements");
        }
        if (maintenanceContractRepository.existsByTenantIdAndCustomerId(tenantId, id)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Impossible de supprimer : client lié à un contrat de maintenance");
        }
        followUpRepository.deleteAll(
                followUpRepository.findByTenantIdAndCustomerIdOrderByScheduledAtDesc(tenantId, id));
        customerRepository.delete(customer);
        auditService.record(tenantId, "Customer", id, "DELETE", Map.of());
    }

    @Transactional
    public FollowUp scheduleFollowUp(UUID customerId, FollowUpRequest request) {
        SecurityContextHelper.requireWriteAccess();
        Customer customer = requireCustomer(customerId);
        if (!customer.getWorkspaceId().equals(request.workspaceId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Workspace mismatch");
        }
        FollowUp followUp = new FollowUp();
        followUp.setTenantId(customer.getTenantId());
        followUp.setWorkspaceId(request.workspaceId());
        followUp.setCustomerId(customerId);
        followUp.setScheduledAt(request.scheduledAt());
        followUp.setActionType(request.actionType());
        followUp.setNotes(request.notes());
        followUp.setCreatedByUserId(SecurityContextHelper.requireUser().userId());
        customer.setNextFollowUpAt(request.scheduledAt());
        customerRepository.save(customer);
        return followUpRepository.save(followUp);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> history(UUID customerId) {
        Customer customer = requireCustomer(customerId);
        UUID tenantId = customer.getTenantId();
        return Map.of(
                "followUps", followUpRepository.findByTenantIdAndCustomerIdOrderByScheduledAtDesc(tenantId, customerId),
                "quotations", quotationRepository.findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(
                        tenantId, customer.getWorkspaceId(), Pageable.ofSize(100)).getContent().stream()
                        .filter(q -> q.getCustomerId().equals(customerId)).toList(),
                "invoices", invoiceRepository.findByTenantIdAndWorkspaceIdOrderByIssueDateDesc(
                        tenantId, customer.getWorkspaceId(), Pageable.ofSize(100)).getContent().stream()
                        .filter(i -> i.getCustomerId().equals(customerId)).toList(),
                "payments", paymentRepository.findByTenantIdAndWorkspaceIdOrderByPaymentDateDesc(
                        tenantId, customer.getWorkspaceId(), Pageable.ofSize(100)).getContent().stream()
                        .filter(p -> p.getCustomerId().equals(customerId)).toList());
    }

    public Customer requireCustomer(UUID id) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        Customer customer = customerRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer not found"));
        workspaceService.requireInTenant(customer.getWorkspaceId(), tenantId);
        return customer;
    }

    private void applyEtsyShippingAddress(
            Customer customer,
            String addressLine1,
            String addressLine2,
            String postalCode,
            String city,
            String countryCode) {
        if (isNotBlank(addressLine1)) {
            customer.setAddressLine1(addressLine1.trim());
        }
        if (isNotBlank(addressLine2)) {
            customer.setAddressLine2(addressLine2.trim());
        }
        if (isNotBlank(postalCode)) {
            customer.setPostalCode(postalCode.trim());
        }
        if (isNotBlank(city)) {
            customer.setCity(city.trim());
        }
        if (isNotBlank(countryCode)) {
            customer.setCountryCode(countryCode.trim().toUpperCase());
        }
    }

    private static void mergeNameIfMissing(Customer customer, String firstName, String lastName) {
        if (blankToNull(customer.getFirstName()) == null && isNotBlank(firstName)) {
            customer.setFirstName(firstName);
        }
        if (blankToNull(customer.getLastName()) == null && isNotBlank(lastName)) {
            customer.setLastName(lastName);
        }
    }

    private static NameParts splitBuyerName(String buyerName) {
        if (buyerName == null || buyerName.isBlank()) {
            return new NameParts("Client", "Etsy");
        }
        String normalized = buyerName.trim().replaceAll("\\s+", " ");
        int i = normalized.indexOf(' ');
        if (i < 0) {
            return new NameParts(normalized, "Etsy");
        }
        return new NameParts(normalized.substring(0, i), normalized.substring(i + 1).trim());
    }

    private static String normalizeCountry(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return "FR";
        }
        String c = countryCode.trim();
        return c.length() >= 2 ? c.substring(0, 2).toUpperCase() : "FR";
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    private record NameParts(String firstName, String lastName) {
    }

    private void applyRequest(Customer customer, CustomerRequest request) {
        customer.setWorkspaceId(request.workspaceId());
        customer.setCustomerType(request.customerType());
        customer.setCompanyName(request.companyName());
        customer.setFirstName(request.firstName());
        customer.setLastName(request.lastName());
        customer.setEmail(request.email());
        customer.setPhone(request.phone());
        customer.setAddressLine1(request.addressLine1());
        customer.setAddressLine2(request.addressLine2());
        customer.setPostalCode(request.postalCode());
        customer.setCity(request.city());
        customer.setCountryCode(request.countryCode() != null ? request.countryCode() : "FR");
        if (request.status() != null) {
            customer.setStatus(request.status());
        }
        customer.setNotes(request.notes());
        if (request.nextFollowUpAt() != null) {
            customer.setNextFollowUpAt(request.nextFollowUpAt());
        }
    }

    private CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getId(), c.getWorkspaceId(), c.getCustomerType(), c.getCompanyName(),
                c.getFirstName(), c.getLastName(), c.getEmail(), c.getPhone(), c.getAddressLine1(),
                c.getAddressLine2(), c.getPostalCode(), c.getCity(), c.getCountryCode(), c.getStatus(),
                c.getNotes(), c.getNextFollowUpAt(), c.isArchived(), c.getCreatedAt(), c.getUpdatedAt());
    }

    private static String blankToNull(String q) {
        return q == null || q.isBlank() ? null : q.trim();
    }

    private static String likePattern(String term) {
        return "%" + term.toLowerCase() + "%";
    }
}
