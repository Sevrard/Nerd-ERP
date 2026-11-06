package com.nerd.erp.domain.workspace;

/** Régime de TVA de l'émetteur, déterminant la mention légale portée sur les documents. */
public enum VatRegime {
    /** Assujetti : TVA facturée aux taux des lignes. */
    NORMAL,
    /** Franchise en base : « TVA non applicable, art. 293 B du CGI ». */
    FRANCHISE_293B;

    public String legalMention() {
        return this == FRANCHISE_293B ? "TVA non applicable, art. 293 B du CGI." : null;
    }
}
