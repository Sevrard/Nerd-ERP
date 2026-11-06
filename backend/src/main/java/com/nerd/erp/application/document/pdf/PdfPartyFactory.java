package com.nerd.erp.application.document.pdf;

import com.nerd.erp.domain.customer.Customer;
import com.nerd.erp.domain.customer.CustomerType;
import com.nerd.erp.domain.workspace.Workspace;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Construit les blocs « Émetteur » / « Destinataire » à partir des entités ou d'un snapshot client. */
public final class PdfPartyFactory {

    private PdfPartyFactory() {
    }

    public static PdfDocumentModel.Party issuer(Workspace w) {
        List<String> lines = new ArrayList<>();
        addIfPresent(lines, w.getLegalSubtitle());
        lines.addAll(addressLines(w.getAddressLine1(), w.getAddressLine2(), w.getPostalCode(), w.getCity(),
                w.getRegion(), w.getCountryCode()));
        addIfPresent(lines, w.getContactEmail());
        addIfPresent(lines, w.getWebsiteUrl());
        String legal = legalLine(w);
        addIfPresent(lines, legal);
        return new PdfDocumentModel.Party(w.getName(), lines);
    }

    public static String legalLine(Workspace w) {
        List<String> parts = new ArrayList<>();
        if (notBlank(w.getSiren())) {
            parts.add("SIREN " + w.getSiren());
        }
        if (notBlank(w.getVatNumber())) {
            parts.add("TVA " + w.getVatNumber());
        }
        return parts.isEmpty() ? null : String.join(" - ", parts);
    }

    public static String addressOneLine(Workspace w) {
        return String.join(", ", addressLines(w.getAddressLine1(), w.getAddressLine2(), w.getPostalCode(),
                w.getCity(), w.getRegion(), w.getCountryCode()));
    }

    public static PdfDocumentModel.Party recipient(Customer c) {
        String title;
        List<String> lines = new ArrayList<>();
        String person = joinNonBlank(" ", c.getFirstName(), c.getLastName());
        if (c.getCustomerType() == CustomerType.COMPANY && notBlank(c.getCompanyName())) {
            title = c.getCompanyName();
            addIfPresent(lines, person);
        } else {
            title = notBlank(person) ? person : Objects.toString(c.getCompanyName(), "Client");
        }
        lines.addAll(addressLines(c.getAddressLine1(), c.getAddressLine2(), c.getPostalCode(), c.getCity(),
                null, c.getCountryCode()));
        addIfPresent(lines, c.getEmail());
        addIfPresent(lines, c.getPhone());
        return new PdfDocumentModel.Party(title, lines);
    }

    public static PdfDocumentModel.Party recipient(Map<String, Object> snapshot) {
        String display = str(snapshot.get("displayName"));
        String company = str(snapshot.get("companyName"));
        String person = joinNonBlank(" ", str(snapshot.get("firstName")), str(snapshot.get("lastName")));
        String title = notBlank(display) ? display : notBlank(company) ? company : notBlank(person) ? person : "Client";
        List<String> lines = new ArrayList<>();
        if (notBlank(company) && !company.equals(title)) {
            lines.add(company);
        }
        if (notBlank(person) && !person.equals(title)) {
            lines.add(person);
        }
        lines.addAll(addressLines(str(snapshot.get("addressLine1")), str(snapshot.get("addressLine2")),
                str(snapshot.get("postalCode")), str(snapshot.get("city")), null, str(snapshot.get("countryCode"))));
        addIfPresent(lines, str(snapshot.get("email")));
        addIfPresent(lines, str(snapshot.get("phone")));
        return new PdfDocumentModel.Party(title, lines);
    }

    private static List<String> addressLines(String line1, String line2, String postalCode, String city,
                                             String region, String countryCode) {
        List<String> lines = new ArrayList<>();
        addIfPresent(lines, line1);
        addIfPresent(lines, line2);
        String cityLine = joinNonBlank(" ", postalCode, city);
        String tail = joinNonBlank(", ", cityLine, region, countryLabel(countryCode));
        addIfPresent(lines, tail);
        return lines;
    }

    private static String countryLabel(String code) {
        if (!notBlank(code)) {
            return null;
        }
        return "FR".equalsIgnoreCase(code) ? "France" : code.toUpperCase();
    }

    private static String joinNonBlank(String sep, String... parts) {
        List<String> kept = new ArrayList<>();
        for (String p : parts) {
            if (notBlank(p)) {
                kept.add(p.trim());
            }
        }
        return String.join(sep, kept);
    }

    private static void addIfPresent(List<String> lines, String value) {
        if (notBlank(value)) {
            lines.add(value.trim());
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
