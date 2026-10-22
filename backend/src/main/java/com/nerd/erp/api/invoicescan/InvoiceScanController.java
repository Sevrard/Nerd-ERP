package com.nerd.erp.api.invoicescan;

import com.nerd.erp.api.invoice.InvoiceResponse;
import com.nerd.erp.application.invoicescan.InvoiceScanService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoice-scan")
public class InvoiceScanController {

    private final InvoiceScanService invoiceScanService;

    public InvoiceScanController(InvoiceScanService invoiceScanService) {
        this.invoiceScanService = invoiceScanService;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InvoiceScanBatchPreviewResponse analyze(
            @RequestParam UUID workspaceId,
            @RequestParam(required = false) UUID merchantId,
            @RequestPart("file") MultipartFile file) {
        return invoiceScanService.analyze(workspaceId, file, merchantId);
    }

    @PostMapping("/commit")
    public InvoiceResponse commit(@Valid @RequestBody InvoiceScanCommitRequest request) {
        return invoiceScanService.commit(request);
    }
}
