package com.nerd.erp.api.quotation;

import com.nerd.erp.api.quotation.PublicQuotationDtos.AcceptRequest;
import com.nerd.erp.api.quotation.PublicQuotationDtos.PublicQuotationView;
import com.nerd.erp.api.quotation.PublicQuotationDtos.RejectRequest;
import com.nerd.erp.application.document.GeneratedDocument;
import com.nerd.erp.application.quotation.QuotationPublicService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Parcours client sans session (lien signé reçu par e-mail) : sous {@code /auth/public} pour le permitAll. */
@RestController
@RequestMapping("/api/v1/auth/public/quotations")
public class PublicQuotationController {

    private final QuotationPublicService publicService;

    public PublicQuotationController(QuotationPublicService publicService) {
        this.publicService = publicService;
    }

    @GetMapping("/{token}")
    public PublicQuotationView view(@PathVariable String token) {
        return publicService.view(token);
    }

    @GetMapping("/{token}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String token) {
        GeneratedDocument doc = publicService.pdf(token);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.filename() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(doc.content());
    }

    @PostMapping("/{token}/accept")
    public PublicQuotationView accept(@PathVariable String token, @Valid @RequestBody AcceptRequest request) {
        return publicService.accept(token, request);
    }

    @PostMapping("/{token}/reject")
    public PublicQuotationView reject(@PathVariable String token, @Valid @RequestBody RejectRequest request) {
        return publicService.reject(token, request);
    }
}
