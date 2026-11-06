package com.nerd.erp.application.importer;

import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.api.importer.CsvImportResult;
import com.nerd.erp.application.audit.AuditService;
import com.nerd.erp.application.workspace.WorkspaceService;
import com.nerd.erp.domain.catalog.BillingType;
import com.nerd.erp.domain.catalog.Product;
import com.nerd.erp.domain.catalog.ProductCategory;
import com.nerd.erp.infrastructure.persistence.catalog.ProductCategoryRepository;
import com.nerd.erp.infrastructure.persistence.catalog.ProductRepository;
import com.nerd.erp.infrastructure.security.core.SecurityContextHelper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Import CSV de produits / prestations. Colonnes : {@code reference} (clé d'upsert, obligatoire), {@code nom}
 * (obligatoire à la création), {@code description}, {@code prix_ht}, {@code tva}, {@code categorie} (libellé ;
 * créée si absente), {@code facturation} (unite|jour|forfait|mois), {@code unite}, {@code actif} (oui|non).
 */
@Service
public class ProductCsvImportService {

    public static final String TEMPLATE = """
            reference;nom;description;prix_ht;tva;categorie;facturation;unite;actif
            DEV-J;Développement sur mesure;Journée de développement full-stack;650;20;Développement;jour;;oui
            AUDIT-SEC;Audit de sécurité;Revue d'architecture et tests d'intrusion;2500;20;Conseil;forfait;;oui
            INFOGERANCE;Infogérance;Supervision et maintenance mensuelle;190;20;Services;mois;;oui
            STICKER-A6;Sticker A6;Impression vinyle laminé;2,50;20;Boutique;unite;pièce;oui
            """;

    private static final int PREVIEW_LIMIT = 20;
    private static final Set<String> TRUE_WORDS = Set.of("oui", "o", "yes", "y", "true", "1", "actif", "x");
    private static final Set<String> FALSE_WORDS = Set.of("non", "n", "no", "false", "0", "inactif");

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final WorkspaceService workspaceService;
    private final AuditService auditService;

    public ProductCsvImportService(ProductRepository productRepository,
                                   ProductCategoryRepository categoryRepository,
                                   WorkspaceService workspaceService,
                                   AuditService auditService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.workspaceService = workspaceService;
        this.auditService = auditService;
    }

    @Transactional
    public CsvImportResult importCsv(UUID workspaceId, byte[] content, boolean dryRun) {
        SecurityContextHelper.requireWriteAccess();
        UUID tenantId = SecurityContextHelper.requireTenantId();
        workspaceService.requireInTenant(workspaceId, tenantId);

        CsvTable table = CsvTable.parse(content);
        if (!table.hasHeader("reference", "ref", "sku", "code")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, CsvTable.INVALID_CSV,
                    "Colonne « reference » introuvable. Téléchargez le modèle pour voir le format.");
        }

        Map<String, Product> existingByRef = new HashMap<>();
        for (Product p : productRepository.listByWorkspace(tenantId, workspaceId, false, null,
                org.springframework.data.domain.Pageable.unpaged()).getContent()) {
            existingByRef.put(p.getReferenceCode().toLowerCase(Locale.ROOT), p);
        }
        Map<String, ProductCategory> categoriesByLabel = new LinkedHashMap<>();
        int maxSort = 0;
        for (ProductCategory c : categoryRepository.findByTenantIdAndWorkspaceIdOrderBySortOrderAscLabelAsc(tenantId, workspaceId)) {
            categoriesByLabel.put(normalize(c.getLabel()), c);
            categoriesByLabel.putIfAbsent(normalize(c.getCode()), c);
            maxSort = Math.max(maxSort, c.getSortOrder());
        }
        List<ProductCategory> newCategories = new ArrayList<>();

        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<CsvImportResult.RowError> errors = new ArrayList<>();
        List<CsvImportResult.RowPreview> preview = new ArrayList<>();
        List<Product> toSave = new ArrayList<>();
        Map<Product, ProductCategory> pendingCategory = new java.util.IdentityHashMap<>();

        for (CsvTable.Row row : table.rows()) {
            try {
                String ref = row.get("reference", "ref", "sku", "code");
                if (ref == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, CsvTable.INVALID_CSV, "Référence manquante.");
                }
                if (ref.length() > 64) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, CsvTable.INVALID_CSV, "Référence trop longue (64 max).");
                }
                String name = row.get("nom", "name", "libelle", "designation");
                String description = row.get("description", "detail");
                BigDecimal price = decimal(row.get("prix_ht", "prix", "price", "unit_price_ht", "pu_ht"), "prix_ht");
                BigDecimal vat = decimal(row.get("tva", "vat", "vat_rate", "taux_tva"), "tva");
                if (vat != null && (vat.signum() < 0 || vat.compareTo(new BigDecimal("100")) > 0)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, CsvTable.INVALID_CSV, "Taux de TVA hors limites : " + vat);
                }
                if (price != null && price.signum() < 0) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, CsvTable.INVALID_CSV, "Prix HT négatif.");
                }
                BillingType billing = billingType(row.get("facturation", "billing", "billing_type", "mode"));
                String unitLabel = row.get("unite", "unit", "unit_label");
                Boolean active = bool(row.get("actif", "active", "statut"));
                String categoryLabel = row.get("categorie", "category", "famille");

                Product existing = existingByRef.get(ref.toLowerCase(Locale.ROOT));
                ProductCategory category = null;
                if (categoryLabel != null) {
                    category = categoriesByLabel.get(normalize(categoryLabel));
                    if (category == null) {
                        category = new ProductCategory();
                        category.setTenantId(tenantId);
                        category.setWorkspaceId(workspaceId);
                        category.setLabel(categoryLabel);
                        category.setCode(uniqueCategoryCode(categoryLabel, categoriesByLabel.values()));
                        category.setSortOrder(++maxSort);
                        categoriesByLabel.put(normalize(categoryLabel), category);
                        newCategories.add(category);
                    }
                }

                String action;
                if (existing == null) {
                    if (name == null) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, CsvTable.INVALID_CSV,
                                "Nom manquant pour la nouvelle référence « " + ref + " ».");
                    }
                    if (price == null) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, CsvTable.INVALID_CSV,
                                "Prix HT manquant pour la nouvelle référence « " + ref + " ».");
                    }
                    Product p = new Product();
                    p.setTenantId(tenantId);
                    p.setWorkspaceId(workspaceId);
                    p.setReferenceCode(ref);
                    p.setName(name);
                    p.setDescription(description);
                    p.setUnitPriceHt(price);
                    p.setVatRate(vat != null ? vat : new BigDecimal("20.00"));
                    p.setBillingType(billing != null ? billing : BillingType.UNIT);
                    p.setUnitLabel(unitLabel);
                    p.setActive(active == null || active);
                    p.setCategoryId(category == null ? null : category.getId());
                    if (category != null && category.getId() == null) {
                        pendingCategory.put(p, category);
                    }
                    toSave.add(p);
                    existingByRef.put(ref.toLowerCase(Locale.ROOT), p);
                    created++;
                    action = "création";
                } else {
                    boolean changed = false;
                    if (name != null && !name.equals(existing.getName())) {
                        existing.setName(name);
                        changed = true;
                    }
                    if (description != null && !description.equals(existing.getDescription())) {
                        existing.setDescription(description);
                        changed = true;
                    }
                    if (price != null && price.compareTo(existing.getUnitPriceHt()) != 0) {
                        existing.setUnitPriceHt(price);
                        changed = true;
                    }
                    if (vat != null && vat.compareTo(existing.getVatRate()) != 0) {
                        existing.setVatRate(vat);
                        changed = true;
                    }
                    if (billing != null && billing != existing.getBillingType()) {
                        existing.setBillingType(billing);
                        changed = true;
                    }
                    if (unitLabel != null && !unitLabel.equals(existing.getUnitLabel())) {
                        existing.setUnitLabel(unitLabel);
                        changed = true;
                    }
                    if (active != null && active != existing.isActive()) {
                        existing.setActive(active);
                        changed = true;
                    }
                    if (category != null && (category.getId() == null || !Objects.equals(category.getId(), existing.getCategoryId()))) {
                        existing.setCategoryId(category.getId());
                        if (category.getId() == null) {
                            pendingCategory.put(existing, category);
                        }
                        changed = true;
                    }
                    if (changed) {
                        toSave.add(existing);
                        updated++;
                        action = "mise à jour";
                    } else {
                        skipped++;
                        action = "inchangé";
                    }
                }
                if (preview.size() < PREVIEW_LIMIT) {
                    preview.add(new CsvImportResult.RowPreview(row.lineNumber(), ref + " — " + (name == null ? existing.getName() : name), action));
                }
            } catch (ApiException e) {
                errors.add(new CsvImportResult.RowError(row.lineNumber(), e.getMessage()));
            }
        }

        if (!dryRun) {
            if (!newCategories.isEmpty()) {
                categoryRepository.saveAll(newCategories);
            }
            // Les produits rattachés à une catégorie créée dans ce lot reçoivent son identifiant une fois persistée.
            pendingCategory.forEach((p, c) -> p.setCategoryId(c.getId()));
            if (!toSave.isEmpty()) {
                productRepository.saveAll(toSave);
            }
            if (!toSave.isEmpty() || !newCategories.isEmpty()) {
                auditService.record(tenantId, "Product", workspaceId, "IMPORT_CSV",
                        Map.of("created", created, "updated", updated, "skipped", skipped,
                                "categoriesCreated", newCategories.size(), "errors", errors.size()));
            }
        }
        return new CsvImportResult(dryRun, table.rows().size(), created, updated, skipped, errors, preview);
    }

    private static BigDecimal decimal(String raw, String column) {
        if (raw == null) {
            return null;
        }
        String s = raw.replace("€", "").replace("%", "").replace("\u00a0", "").replace(" ", "").replace(',', '.');
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, CsvTable.INVALID_CSV,
                    "Valeur numérique invalide pour « " + column + " » : " + raw);
        }
    }

    private static BillingType billingType(String raw) {
        if (raw == null) {
            return null;
        }
        String s = normalize(raw);
        if (s.startsWith("unit") || s.startsWith("piece") || s.startsWith("qte")) {
            return BillingType.UNIT;
        }
        for (BillingType t : BillingType.values()) {
            if (normalize(t.name()).equals(s) || normalize(t.defaultUnitLabel()).startsWith(s)) {
                return t;
            }
        }
        if (s.startsWith("jour") || s.startsWith("day") || s.startsWith("tjm")) {
            return BillingType.DAYS;
        }
        if (s.startsWith("forf") || s.startsWith("fixed") || s.startsWith("flat")) {
            return BillingType.FIXED_PRICE;
        }
        if (s.startsWith("mois") || s.startsWith("month") || s.startsWith("mens") || s.startsWith("abo") || s.startsWith("recur")) {
            return BillingType.RECURRING;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, CsvTable.INVALID_CSV,
                "Mode de facturation inconnu « " + raw + " » (unite, jour, forfait, mois).");
    }

    private static Boolean bool(String raw) {
        if (raw == null) {
            return null;
        }
        String s = normalize(raw);
        if (TRUE_WORDS.contains(s)) {
            return true;
        }
        if (FALSE_WORDS.contains(s)) {
            return false;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, CsvTable.INVALID_CSV,
                "Valeur « actif » inconnue : " + raw + " (oui/non).");
    }

    private static String uniqueCategoryCode(String label, Iterable<ProductCategory> existing) {
        String base = Normalizer.normalize(label, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isBlank()) {
            base = "CAT";
        }
        if (base.length() > 40) {
            base = base.substring(0, 40);
        }
        Set<String> codes = new java.util.HashSet<>();
        for (ProductCategory c : existing) {
            codes.add(c.getCode().toUpperCase(Locale.ROOT));
        }
        String candidate = base;
        for (int i = 2; codes.contains(candidate); i++) {
            candidate = base + "_" + i;
        }
        return candidate;
    }

    private static String normalize(String v) {
        return v == null ? "" : CsvTable.normalizeHeader(v);
    }
}
