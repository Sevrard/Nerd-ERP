package com.nerd.erp.application.maintenance;

import com.nerd.erp.application.catalog.ProductService;
import com.nerd.erp.application.customer.CustomerService;
import com.nerd.erp.domain.maintenance.MaintenanceContract;
import com.nerd.erp.domain.maintenance.MaintenanceContractStatus;
import com.nerd.erp.domain.maintenance.MaintenancePeriodicity;
import com.nerd.erp.api.maintenance.MaintenanceContractRequest;
import com.nerd.erp.api.maintenance.MaintenanceContractResponse;
import com.nerd.erp.infrastructure.persistence.maintenance.MaintenanceContractRepository;
import com.nerd.erp.infrastructure.security.core.SecurityContextHelper;
import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.api.shared.PageResponse;
import com.nerd.erp.application.workspace.WorkspaceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class MaintenanceContractService {

    private final MaintenanceContractRepository repository;
    private final CustomerService customerService;
    private final ProductService productService;
    private final WorkspaceService workspaceService;

    public MaintenanceContractService(MaintenanceContractRepository repository,
                                      CustomerService customerService,
                                      ProductService productService,
                                      WorkspaceService workspaceService) {
        this.repository = repository;
        this.customerService = customerService;
        this.productService = productService;
        this.workspaceService = workspaceService;
    }

    @Transactional(readOnly = true)
    public PageResponse<MaintenanceContractResponse> list(UUID workspaceId, Pageable pageable) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        workspaceService.requireInTenant(workspaceId, tenantId);
        Page<MaintenanceContract> page = repository.findByTenantIdAndWorkspaceIdOrderByNextRenewalDateAsc(
                tenantId, workspaceId, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional
    public MaintenanceContractResponse create(MaintenanceContractRequest request) {
        SecurityContextHelper.requireWriteAccess();
        UUID tenantId = SecurityContextHelper.requireTenantId();
        workspaceService.requireInTenant(request.workspaceId(), tenantId);
        var customer = customerService.requireCustomer(request.customerId());
        if (!customer.getWorkspaceId().equals(request.workspaceId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Customer workspace mismatch");
        }
        if (request.productId() != null) {
            productService.requireProduct(request.productId(), tenantId);
        }
        MaintenanceContract contract = new MaintenanceContract();
        contract.setTenantId(tenantId);
        apply(contract, request);
        return toResponse(repository.save(contract));
    }

    @Transactional
    public MaintenanceContractResponse update(UUID id, MaintenanceContractRequest request) {
        SecurityContextHelper.requireWriteAccess();
        MaintenanceContract contract = require(id);
        apply(contract, request);
        return toResponse(repository.save(contract));
    }

    private MaintenanceContract require(UUID id) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        MaintenanceContract contract = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Maintenance contract not found"));
        workspaceService.requireInTenant(contract.getWorkspaceId(), tenantId);
        return contract;
    }

    private void apply(MaintenanceContract contract, MaintenanceContractRequest request) {
        contract.setWorkspaceId(request.workspaceId());
        contract.setCustomerId(request.customerId());
        contract.setProductId(request.productId());
        contract.setTitle(request.title());
        contract.setDescription(request.description());
        contract.setPeriodicity(request.periodicity() != null ? request.periodicity() : MaintenancePeriodicity.MONTHLY);
        contract.setAmountHt(request.amountHt());
        contract.setVatRate(request.vatRate() != null ? request.vatRate() : new BigDecimal("20.00"));
        contract.setStartDate(request.startDate());
        contract.setEndDate(request.endDate());
        contract.setNextRenewalDate(request.nextRenewalDate());
        if (request.status() != null) {
            contract.setStatus(request.status());
        } else if (contract.getStatus() == null) {
            contract.setStatus(MaintenanceContractStatus.ACTIVE);
        }
    }

    private MaintenanceContractResponse toResponse(MaintenanceContract c) {
        return new MaintenanceContractResponse(c.getId(), c.getWorkspaceId(), c.getCustomerId(), c.getProductId(),
                c.getTitle(), c.getDescription(), c.getPeriodicity(), c.getAmountHt(), c.getVatRate(),
                c.getStartDate(), c.getEndDate(), c.getNextRenewalDate(), c.getStatus());
    }
}
