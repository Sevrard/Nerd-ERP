package com.nerd.erp.application.importer;

import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.api.importer.CsvImportResult;
import com.nerd.erp.application.audit.AuditService;
import com.nerd.erp.application.workspace.WorkspaceService;
import com.nerd.erp.domain.customer.Customer;
import com.nerd.erp.domain.customer.CustomerStatus;
import com.nerd.erp.domain.customer.CustomerType;
import com.nerd.erp.infrastructure.persistence.customer.CustomerRepository;
import com.nerd.erp.infrastructure.security.core.SecurityContextHelper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Import CSV de clients. Colonnes reconnues (ordre libre, en-têtes insensibles à la casse/accents) :
 * {@code type} (société|particulier), {@code raison_sociale}, {@code prenom}, {@code nom}, {@code email},
 * {@code telephone}, {@code adresse}, {@code adresse2}, {@code code_postal}, {@code ville}, {@code pays},
 * {@code statut} (prospect|client|inactif), {@code notes}.
 *
 * <p>Dédoublonnage : e-mail, sinon raison sociale, sinon nom + prénom (dans le workspace, hors archivés). Une
 * correspondance met à jour les champs renseignés dans le fichier (les colonnes vides ne vident rien).
 */
@Service
public class CustomerCsvImportService {

    public static final String TEMPLATE = """
            type;raison_sociale;prenom;nom;email;telephone;adresse;adresse2;code_postal;ville;pays;statut;notes
            societe;ACME SAS;;;contact@acme.fr;01 23 45 67 89;1 rue de la Paix;;75001;Paris;FR;client;Client historique
            particulier;;Marie;Dupont;marie.dupont@example.org;06 12 34 56 78;12 avenue Victor Hugo;Bât. B;69006;Lyon;FR;prospect;
            """;

    private static final int PREVIEW_LIMIT = 20;

    private final CustomerRepository customerRepository;
    private final WorkspaceService workspaceService;
    private final AuditService auditService;

    public CustomerCsvImportService(CustomerRepository customerRepository,
                                    WorkspaceService workspaceService,
                                    AuditService auditService) {
        this.customerRepository = customerRepository;
        this.workspaceService = workspaceService;
        this.auditService = auditService;
    }

    @Transactional
    public CsvImportResult importCsv(UUID workspaceId, byte[] content, boolean dryRun) {
        SecurityContextHelper.requireWriteAccess();
        UUID tenantId = SecurityContextHelper.requireTenantId();
        workspaceService.requireInTenant(workspaceId, tenantId);

        CsvTable table = CsvTable.parse(content);
        if (!table.hasHeader("raison_sociale", "societe", "entreprise", "company")
                && !table.hasHeader("nom", "lastname", "last_name")
                && !table.hasHeader("email", "e-mail", "courriel")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, CsvTable.INVALID_CSV,
                    "Colonnes attendues introuvables : au moins « raison_sociale », « nom » ou « email ». "
                            + "Téléchargez le modèle pour voir le format.");
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<CsvImportResult.RowError> errors = new ArrayList<>();
        List<CsvImportResult.RowPreview> preview = new ArrayList<>();
        List<Customer> toSave = new ArrayList<>();
        // Les lignes créées dans ce même fichier doivent aussi servir au dédoublonnage.
        List<Customer> pending = new ArrayList<>();

        for (CsvTable.Row row : table.rows()) {
            try {
                Parsed p = parse(row);
                Customer existing = findExisting(tenantId, workspaceId, p, pending);
                String action;
                if (existing == null) {
                    Customer c = new Customer();
                    c.setTenantId(tenantId);
                    c.setWorkspaceId(workspaceId);
                    c.setCustomerType(p.type);
                    c.setCountryCode("FR");
                    c.setStatus(CustomerStatus.PROSPECT);
                    apply(c, p);
                    toSave.add(c);
                    pending.add(c);
                    created++;
                    action = "création";
                } else if (apply(existing, p)) {
                    toSave.add(existing);
                    updated++;
                    action = "mise à jour";
                } else {
                    skipped++;
                    action = "inchangé";
                }
                if (preview.size() < PREVIEW_LIMIT) {
                    preview.add(new CsvImportResult.RowPreview(row.lineNumber(), p.label(), action));
                }
            } catch (ApiException e) {
                errors.add(new CsvImportResult.RowError(row.lineNumber(), e.getMessage()));
            }
        }

        if (!dryRun && !toSave.isEmpty()) {
            customerRepository.saveAll(toSave);
            auditService.record(tenantId, "Customer", workspaceId, "IMPORT_CSV",
                    Map.of("created", created, "updated", updated, "skipped", skipped, "errors", errors.size()));
        }
        return new CsvImportResult(dryRun, table.rows().size(), created, updated, skipped, errors, preview);
    }

    private record Parsed(CustomerType type, String companyName, String firstName, String lastName, String email,
                          String phone, String addressLine1, String addressLine2, String postalCode, String city,
                          String countryCode, CustomerStatus status, String notes) {
        String label() {
            if (type == CustomerType.COMPANY) {
                return companyName;
            }
            return ((firstName == null ? "" : firstName + " ") + (lastName == null ? "" : lastName)).trim();
        }
    }

    private static Parsed parse(CsvTable.Row row) {
        String company = row.get("raison_sociale", "societe", "entreprise", "company");
        String firstName = row.get("prenom", "firstname", "first_name");
        String lastName = row.get("nom", "lastname", "last_name");
        String email = lower(row.get("email", "e-mail", "courriel", "mail"));
        String typeRaw = row.get("type", "customer_type");
        CustomerType type;
        if (typeRaw != null) {
            String t = typeRaw.toLowerCase(Locale.ROOT);
            if (t.startsWith("soc") || t.startsWith("ent") || t.startsWith("comp") || t.equals("pro")) {
                type = CustomerType.COMPANY;
            } else if (t.startsWith("part") || t.startsWith("ind") || t.startsWith("priv")) {
                type = CustomerType.INDIVIDUAL;
            } else {
                throw new ApiException(HttpStatus.BAD_REQUEST, CsvTable.INVALID_CSV,
                        "Type inconnu « " + typeRaw + "» (attendu : societe ou particulier).");
            }
        } else {
            type = company != null ? CustomerType.COMPANY : CustomerType.INDIVIDUAL;
        }
        if (type == CustomerType.COMPANY && company == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, CsvTable.INVALID_CSV, "Raison sociale manquante.");
        }
        if (type == CustomerType.INDIVIDUAL && lastName == null && email == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, CsvTable.INVALID_CSV, "Nom (ou e-mail) manquant.");
        }
        if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, CsvTable.INVALID_CSV, "E-mail invalide : " + email);
        }
        String statusRaw = row.get("statut", "status");
        CustomerStatus status = null;
        if (statusRaw != null) {
            String s = statusRaw.toLowerCase(Locale.ROOT);
            if (s.startsWith("pros")) {
                status = CustomerStatus.PROSPECT;
            } else if (s.startsWith("cli")) {
                status = CustomerStatus.CLIENT;
            } else if (s.startsWith("ina")) {
                status = CustomerStatus.INACTIVE;
            } else {
                throw new ApiException(HttpStatus.BAD_REQUEST, CsvTable.INVALID_CSV,
                        "Statut inconnu « " + statusRaw + "» (attendu : prospect, client ou inactif).");
            }
        }
        String country = row.get("pays", "country", "country_code");
        if (country != null) {
            country = country.trim().toUpperCase(Locale.ROOT);
            if (country.startsWith("FRANCE")) {
                country = "FR";
            }
            if (country.length() != 2) {
                throw new ApiException(HttpStatus.BAD_REQUEST, CsvTable.INVALID_CSV,
                        "Pays attendu en code ISO 2 lettres (ex. FR) : « " + country + " ».");
            }
        }
        return new Parsed(type, company, firstName, lastName, email,
                row.get("telephone", "tel", "phone", "mobile"),
                row.get("adresse", "adresse1", "address", "address_line1"),
                row.get("adresse2", "complement", "address_line2"),
                row.get("code_postal", "cp", "postal_code", "zip"),
                row.get("ville", "city"),
                country, status, row.get("notes", "commentaire", "remarques"));
    }

    private Customer findExisting(UUID tenantId, UUID workspaceId, Parsed p, List<Customer> pending) {
        for (Customer c : pending) {
            if (matches(c, p)) {
                return c;
            }
        }
        if (p.email != null) {
            var byEmail = customerRepository.findByTenantIdAndWorkspaceIdAndArchivedFalseAndEmailIgnoreCase(
                    tenantId, workspaceId, p.email);
            if (byEmail.isPresent()) {
                return byEmail.get();
            }
        }
        if (p.type == CustomerType.COMPANY) {
            List<Customer> byName = customerRepository
                    .findByTenantIdAndWorkspaceIdAndArchivedFalseAndCompanyNameIgnoreCase(tenantId, workspaceId, p.companyName);
            return byName.isEmpty() ? null : byName.getFirst();
        }
        if (p.lastName != null) {
            List<Customer> byName = customerRepository
                    .findByTenantIdAndWorkspaceIdAndArchivedFalseAndFirstNameIgnoreCaseAndLastNameIgnoreCase(
                            tenantId, workspaceId, p.firstName == null ? "" : p.firstName, p.lastName);
            return byName.isEmpty() ? null : byName.getFirst();
        }
        return null;
    }

    private static boolean matches(Customer c, Parsed p) {
        if (p.email != null && c.getEmail() != null) {
            return p.email.equalsIgnoreCase(c.getEmail());
        }
        if (p.type == CustomerType.COMPANY) {
            return p.companyName.equalsIgnoreCase(c.getCompanyName());
        }
        return p.lastName != null && p.lastName.equalsIgnoreCase(c.getLastName())
                && (p.firstName == null ? "" : p.firstName).equalsIgnoreCase(c.getFirstName() == null ? "" : c.getFirstName());
    }

    /** Applique les valeurs non vides ; renvoie {@code true} si quelque chose a changé. */
    private static boolean apply(Customer c, Parsed p) {
        boolean changed = false;
        changed |= set(c.getCompanyName(), p.companyName, c::setCompanyName);
        changed |= set(c.getFirstName(), p.firstName, c::setFirstName);
        changed |= set(c.getLastName(), p.lastName, c::setLastName);
        changed |= set(c.getEmail(), p.email, c::setEmail);
        changed |= set(c.getPhone(), p.phone, c::setPhone);
        changed |= set(c.getAddressLine1(), p.addressLine1, c::setAddressLine1);
        changed |= set(c.getAddressLine2(), p.addressLine2, c::setAddressLine2);
        changed |= set(c.getPostalCode(), p.postalCode, c::setPostalCode);
        changed |= set(c.getCity(), p.city, c::setCity);
        changed |= set(c.getCountryCode(), p.countryCode, c::setCountryCode);
        changed |= set(c.getNotes(), p.notes, c::setNotes);
        if (p.status != null && c.getStatus() != p.status) {
            c.setStatus(p.status);
            changed = true;
        }
        return changed;
    }

    private static boolean set(String current, String next, java.util.function.Consumer<String> setter) {
        if (next == null || next.equals(current)) {
            return false;
        }
        setter.accept(next);
        return true;
    }

    private static String lower(String v) {
        return v == null ? null : v.toLowerCase(Locale.ROOT);
    }
}
