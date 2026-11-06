package com.nerd.erp.application.workspace;

import com.nerd.erp.api.workspace.WorkspaceResponse;
import com.nerd.erp.domain.workspace.SequenceReset;
import com.nerd.erp.domain.workspace.VatRegime;
import com.nerd.erp.domain.workspace.Workspace;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Garde-fou contre l'oubli d'un champ dans le mapping par builder :
 * une entité entièrement renseignée doit produire une réponse sans aucun composant null.
 */
class WorkspaceMapperTest {

    private final WorkspaceMapper mapper = new WorkspaceMapper();

    @Test
    void everyResponseComponentIsMapped() throws Exception {
        Workspace w = new Workspace();
        w.setId(UUID.randomUUID());
        w.setTenantId(UUID.randomUUID());
        w.setOrganizationId(UUID.randomUUID());
        w.setName("Nerd Group");
        w.setSlug("nerd-group");
        w.setBrandColor("#06b6d4");
        w.setLogoUrl("https://example.test/logo.png");
        w.setCurrencyCode("EUR");
        w.setInvoicePrefix("FAC");
        w.setQuotationPrefix("DEV");
        w.setLegalSubtitle("Ingénierie");
        w.setAddressLine1("1 rue du Code");
        w.setAddressLine2("Bât. B");
        w.setPostalCode("75000");
        w.setCity("Paris");
        w.setRegion("Île-de-France");
        w.setCountryCode("FR");
        w.setContactEmail("contact@nerd.test");
        w.setWebsiteUrl("https://nerd.test");
        w.setSiren("123456789");
        w.setVatNumber("FR12123456789");
        w.setIban("FR7613051492000123456789012");
        w.setBic("NERDFRPPXXX");
        w.setActive(true);
        w.setCreditNotePrefix("AV");
        w.setVatRegime(VatRegime.FRANCHISE_293B);
        w.setPaymentTermsDays(45);
        w.setLatePenaltyRate(new BigDecimal("10.50"));
        w.setRecoveryFeeAmount(new BigDecimal("40"));
        w.setEarlyPaymentDiscount("2 % sous 8 jours");
        w.setPaymentTermsText("Paiement à réception.");
        w.setLegalFooterText("RCS Paris 123 456 789");
        w.setRemindersEnabled(true);
        w.setNumberPattern("{PREFIX}-{YYYY}{MM}-{SEQ:4}");
        w.setSequenceReset(SequenceReset.MONTHLY);

        WorkspaceResponse response = mapper.toResponse(w);

        for (RecordComponent component : WorkspaceResponse.class.getRecordComponents()) {
            Object value = component.getAccessor().invoke(response);
            assertNotNull(value, "Champ non mappé dans WorkspaceMapper : " + component.getName());
        }
        assertEquals(w.getId(), response.id());
        assertEquals("NERDFRPPXXX", response.bic());
        assertEquals("DEV", response.quotationPrefix());
        assertEquals(VatRegime.FRANCHISE_293B, response.vatRegime());
        assertEquals(45, response.paymentTermsDays());
        assertEquals(SequenceReset.MONTHLY, response.sequenceReset());
    }
}
