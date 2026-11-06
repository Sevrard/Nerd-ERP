package com.nerd.erp.api.customer;

import com.nerd.erp.domain.customer.CustomerStatus;
import com.nerd.erp.domain.customer.FollowUp;
import com.nerd.erp.api.customer.CustomerRequest;
import com.nerd.erp.api.customer.CustomerResponse;
import com.nerd.erp.api.customer.FollowUpRequest;
import com.nerd.erp.api.importer.CsvImportResult;
import com.nerd.erp.application.customer.CustomerService;
import com.nerd.erp.application.importer.CustomerCsvImportService;
import com.nerd.erp.api.shared.PageResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import com.nerd.erp.infrastructure.security.core.RequireWriteAccess;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerCsvImportService csvImportService;

    public CustomerController(CustomerService customerService, CustomerCsvImportService csvImportService) {
        this.customerService = customerService;
        this.csvImportService = csvImportService;
    }

    @GetMapping
    public PageResponse<CustomerResponse> search(
            @RequestParam UUID workspaceId,
            @RequestParam(required = false) CustomerStatus status,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return customerService.search(workspaceId, status, q, pageable);
    }

    @GetMapping("/stats")
    public CustomerStatsResponse stats(@RequestParam UUID workspaceId) {
        return customerService.stats(workspaceId);
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable UUID id) {
        return customerService.get(id);
    }

    @PostMapping
    @RequireWriteAccess
    public CustomerResponse create(@Valid @RequestBody CustomerRequest request) {
        return customerService.create(request);
    }

    @PutMapping("/{id}")
    @RequireWriteAccess
    public CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }

    @PostMapping("/{id}/archive")
    @RequireWriteAccess
    public CustomerResponse archive(@PathVariable UUID id) {
        return customerService.archive(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireWriteAccess
    public void delete(@PathVariable UUID id) {
        customerService.delete(id);
    }

    @GetMapping("/{id}/history")
    public Map<String, Object> history(@PathVariable UUID id) {
        return customerService.history(id);
    }

    @PostMapping("/{id}/follow-ups")
    @RequireWriteAccess
    public FollowUp scheduleFollowUp(@PathVariable UUID id, @Valid @RequestBody FollowUpRequest request) {
        return customerService.scheduleFollowUp(id, request);
    }

    /** Import CSV (multipart « file ») ; {@code dryRun=true} pour un aperçu sans écriture. */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireWriteAccess
    public CsvImportResult importCsv(@RequestParam UUID workspaceId,
                                     @RequestParam(defaultValue = "false") boolean dryRun,
                                     @RequestPart("file") MultipartFile file) throws IOException {
        return csvImportService.importCsv(workspaceId, file.getBytes(), dryRun);
    }

    @GetMapping(value = "/import/template", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> importTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modele-clients.csv\"")
                .body(("\uFEFF" + CustomerCsvImportService.TEMPLATE).getBytes(StandardCharsets.UTF_8));
    }
}
