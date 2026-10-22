package com.nerd.erp.application.payment;

import com.nerd.erp.application.customer.CustomerService;
import com.nerd.erp.domain.invoice.Invoice;
import com.nerd.erp.domain.invoice.InvoiceStatus;
import com.nerd.erp.infrastructure.persistence.invoice.InvoiceRepository;
import com.nerd.erp.application.invoice.InvoiceService;
import com.nerd.erp.domain.payment.Payment;
import com.nerd.erp.domain.payment.PaymentAllocation;
import com.nerd.erp.domain.payment.PaymentMethod;
import com.nerd.erp.api.payment.PaymentRequest;
import com.nerd.erp.api.payment.PaymentResponse;
import com.nerd.erp.infrastructure.persistence.payment.PaymentRepository;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final CustomerService customerService;
    private final WorkspaceService workspaceService;
    private final AuditService auditService;

    public PaymentService(PaymentRepository paymentRepository,
                          InvoiceRepository invoiceRepository,
                          InvoiceService invoiceService,
                          CustomerService customerService,
                          WorkspaceService workspaceService,
                          AuditService auditService) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceService = invoiceService;
        this.customerService = customerService;
        this.workspaceService = workspaceService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> list(UUID workspaceId, Pageable pageable) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        workspaceService.requireInTenant(workspaceId, tenantId);
        Page<Payment> page = paymentRepository.findByTenantIdAndWorkspaceIdOrderByPaymentDateDesc(
                tenantId, workspaceId, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional
    public PaymentResponse record(PaymentRequest request) {
        SecurityContextHelper.requireWriteAccess();
        UUID tenantId = SecurityContextHelper.requireTenantId();
        workspaceService.requireInTenant(request.workspaceId(), tenantId);
        var customer = customerService.requireCustomer(request.customerId());
        if (!customer.getWorkspaceId().equals(request.workspaceId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Customer workspace mismatch");
        }

        BigDecimal allocated = request.allocations().stream()
                .map(PaymentRequest.AllocationRequest::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocated.compareTo(request.amount()) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Allocations exceed payment amount");
        }

        Payment payment = new Payment();
        payment.setTenantId(tenantId);
        payment.setWorkspaceId(request.workspaceId());
        payment.setCustomerId(request.customerId());
        payment.setAmount(request.amount());
        payment.setPaymentDate(request.paymentDate());
        payment.setMethod(request.method() != null ? request.method() : PaymentMethod.TRANSFER);
        payment.setReference(request.reference());
        payment.setNotes(request.notes());

        List<PaymentAllocation> allocations = new ArrayList<>();
        for (PaymentRequest.AllocationRequest allocReq : request.allocations()) {
            Invoice invoice = invoiceService.requireInvoice(allocReq.invoiceId());
            if (!invoice.getWorkspaceId().equals(request.workspaceId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invoice workspace mismatch");
            }
            if (invoice.getStatus() == InvoiceStatus.DRAFT || invoice.getStatus() == InvoiceStatus.CANCELLED) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invoice cannot receive payments");
            }
            BigDecimal remaining = invoice.getTotalTtc().subtract(invoice.getAmountPaid());
            if (allocReq.amount().compareTo(remaining) > 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Allocation exceeds invoice balance");
            }
            PaymentAllocation allocation = new PaymentAllocation();
            allocation.setTenantId(tenantId);
            allocation.setPayment(payment);
            allocation.setInvoiceId(invoice.getId());
            allocation.setAmount(allocReq.amount());
            allocations.add(allocation);

            invoice.setAmountPaid(invoice.getAmountPaid().add(allocReq.amount()));
            updateInvoicePaymentStatus(invoice);
            invoiceRepository.save(invoice);
        }
        payment.getAllocations().addAll(allocations);
        payment = paymentRepository.save(payment);
        auditService.record(tenantId, "Payment", payment.getId(), "CREATE", Map.of());
        return toResponse(payment);
    }

    private void updateInvoicePaymentStatus(Invoice invoice) {
        if (invoice.getAmountPaid().compareTo(invoice.getTotalTtc()) >= 0) {
            invoice.setStatus(InvoiceStatus.PAID);
        } else if (invoice.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        }
    }

    private PaymentResponse toResponse(Payment payment) {
        var allocations = payment.getAllocations().stream()
                .map(a -> new PaymentResponse.AllocationResponse(a.getInvoiceId(), a.getAmount()))
                .toList();
        return new PaymentResponse(payment.getId(), payment.getWorkspaceId(), payment.getCustomerId(),
                payment.getAmount(), payment.getPaymentDate(), payment.getMethod(), payment.getReference(),
                payment.getNotes(), allocations);
    }
}
