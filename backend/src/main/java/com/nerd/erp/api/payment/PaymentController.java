package com.nerd.erp.api.payment;

import com.nerd.erp.api.payment.PaymentRequest;
import com.nerd.erp.api.payment.PaymentResponse;
import com.nerd.erp.application.payment.PaymentService;
import com.nerd.erp.api.shared.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public PageResponse<PaymentResponse> list(
            @RequestParam UUID workspaceId,
            @PageableDefault(size = 20, sort = "paymentDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return paymentService.list(workspaceId, pageable);
    }

    @PostMapping
    public PaymentResponse record(@Valid @RequestBody PaymentRequest request) {
        return paymentService.record(request);
    }
}
