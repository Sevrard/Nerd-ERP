package com.nerd.erp.application.catalog;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extrait unité / type de facturation depuis la description produit (import catalogue). */
public final class ProductCatalogText {

    private static final Pattern BILLING = Pattern.compile("Facturation :\\s*(\\w+)");
    private static final Pattern QTY_UNIT = Pattern.compile("Quantité indicative :\\s*[\\d.,]+\\s*(\\S+)");

    private ProductCatalogText() {
    }

    public static String unitLabelFromDescription(String description) {
        if (description == null || description.isBlank()) {
            return "unité";
        }
        Matcher bill = BILLING.matcher(description);
        String billingType = bill.find() ? bill.group(1) : "";
        Matcher qty = QTY_UNIT.matcher(description);
        String unit = qty.find() ? qty.group(1) : "unité";
        if ("FIXED_PRICE".equals(billingType)) {
            return "forfait";
        }
        if ("RECURRING".equals(billingType)) {
            return unit;
        }
        return unit;
    }
}
