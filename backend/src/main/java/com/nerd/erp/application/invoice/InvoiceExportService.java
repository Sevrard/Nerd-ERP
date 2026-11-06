package com.nerd.erp.application.invoice;

import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.application.workspace.WorkspaceService;
import com.nerd.erp.domain.invoice.Invoice;
import com.nerd.erp.domain.invoice.InvoiceStatus;
import com.nerd.erp.infrastructure.persistence.invoice.InvoiceRepository;
import com.nerd.erp.infrastructure.security.core.SecurityContextHelper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Export comptable des factures et avoirs émis : CSV « ; », décimales à la virgule, UTF-8 avec BOM
 * (ouverture directe dans Excel / LibreOffice, import expert-comptable).
 */
@Service
public class InvoiceExportService {

    public record CsvExport(byte[] content, String filename) {
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String[] HEADER = {
            "Type", "Numéro", "Date", "Échéance", "Statut", "Client", "SIREN client", "N° TVA client",
            "Total HT", "Total TVA", "Total TTC", "Encaissé", "Reste dû", "Devise",
            "Facture d'origine", "Référence externe", "Envoyée le"
    };

    private final InvoiceRepository invoiceRepository;
    private final WorkspaceService workspaceService;

    public InvoiceExportService(InvoiceRepository invoiceRepository, WorkspaceService workspaceService) {
        this.invoiceRepository = invoiceRepository;
        this.workspaceService = workspaceService;
    }

    @Transactional(readOnly = true)
    public CsvExport exportCsv(UUID workspaceId, LocalDate from, LocalDate to) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        var workspace = workspaceService.requireInTenant(workspaceId, tenantId);
        if (from == null || to == null || to.isBefore(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Période invalide");
        }
        List<Invoice> invoices = invoiceRepository.findForExport(tenantId, workspaceId, from, to);
        Map<UUID, String> numbers = invoices.stream()
                .collect(java.util.stream.Collectors.toMap(Invoice::getId, Invoice::getNumber, (a, b) -> a));

        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append(String.join(";", HEADER)).append("\r\n");
        for (Invoice i : invoices) {
            String creditedNumber = i.getCreditedInvoiceId() == null ? "" : numbers.getOrDefault(i.getCreditedInvoiceId(),
                    invoiceRepository.findByIdAndTenantId(i.getCreditedInvoiceId(), tenantId).map(Invoice::getNumber).orElse(""));
            Map<String, Object> snap = i.getSnapshotCustomer() == null ? Map.of() : i.getSnapshotCustomer();
            String[] row = {
                    i.isCreditNote() ? "Avoir" : "Facture",
                    i.getNumber(),
                    DATE_FMT.format(i.getIssueDate()),
                    i.isCreditNote() || i.getDueDate() == null ? "" : DATE_FMT.format(i.getDueDate()),
                    statusLabel(i.getStatus()),
                    customerLabel(snap),
                    str(snap.get("siren")),
                    str(snap.get("vatNumber")),
                    dec(i.getTotalHt()),
                    dec(i.getTotalVat()),
                    dec(i.getTotalTtc()),
                    dec(i.getAmountPaid()),
                    i.isCreditNote() || i.getStatus() == InvoiceStatus.CANCELLED ? dec(BigDecimal.ZERO) : dec(i.remainingAmount()),
                    Objects.toString(workspace.getCurrencyCode(), "EUR"),
                    creditedNumber,
                    Objects.toString(i.getExternalReference(), ""),
                    i.getSentAt() == null ? "" : DATE_FMT.format(LocalDate.ofInstant(i.getSentAt(), java.time.ZoneId.systemDefault()))
            };
            sb.append(String.join(";", escapeAll(row))).append("\r\n");
        }
        String filename = "export-factures-" + FILE_FMT.format(from) + "-" + FILE_FMT.format(to) + ".csv";
        return new CsvExport(sb.toString().getBytes(StandardCharsets.UTF_8), filename);
    }

    private static String customerLabel(Map<String, Object> snap) {
        String display = str(snap.get("displayName"));
        if (!display.isBlank()) {
            return display;
        }
        String company = str(snap.get("companyName"));
        if (!company.isBlank()) {
            return company;
        }
        return (str(snap.get("firstName")) + " " + str(snap.get("lastName"))).trim();
    }

    private static String statusLabel(InvoiceStatus status) {
        return switch (status) {
            case DRAFT -> "Brouillon";
            case ISSUED -> "Émise";
            case PARTIALLY_PAID -> "Partiellement payée";
            case PAID -> "Payée";
            case CANCELLED -> "Annulée";
        };
    }

    private static String dec(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String[] escapeAll(String[] row) {
        String[] out = new String[row.length];
        for (int k = 0; k < row.length; k++) {
            String v = row[k] == null ? "" : row[k];
            out[k] = v.contains(";") || v.contains("\"") || v.contains("\n")
                    ? "\"" + v.replace("\"", "\"\"") + "\""
                    : v;
        }
        return out;
    }
}
