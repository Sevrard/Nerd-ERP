package com.nerd.erp.api.maintenance;

import com.nerd.erp.api.maintenance.MaintenanceContractRequest;
import com.nerd.erp.api.maintenance.MaintenanceContractResponse;
import com.nerd.erp.application.maintenance.MaintenanceContractService;
import com.nerd.erp.api.shared.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/maintenance-contracts")
public class MaintenanceContractController {

    private final MaintenanceContractService service;

    public MaintenanceContractController(MaintenanceContractService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<MaintenanceContractResponse> list(
            @RequestParam UUID workspaceId,
            @PageableDefault(size = 20, sort = "nextRenewalDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.list(workspaceId, pageable);
    }

    @PostMapping
    public MaintenanceContractResponse create(@Valid @RequestBody MaintenanceContractRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public MaintenanceContractResponse update(@PathVariable UUID id,
                                              @Valid @RequestBody MaintenanceContractRequest request) {
        return service.update(id, request);
    }
}
