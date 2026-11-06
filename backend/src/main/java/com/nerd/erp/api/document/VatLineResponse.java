package com.nerd.erp.api.document;

import com.nerd.erp.application.money.MoneyCalculator;

import java.math.BigDecimal;
import java.util.List;

/** Récapitulatif TVA par taux d'un document (base HT nette, montant de TVA). */
public record VatLineResponse(BigDecimal rate, BigDecimal baseHt, BigDecimal vatAmount) {

    public static VatLineResponse of(MoneyCalculator.VatLine line) {
        return new VatLineResponse(line.rate(), line.baseHt(), line.vatAmount());
    }

    public static List<VatLineResponse> of(List<MoneyCalculator.VatLine> lines) {
        return lines.stream().map(VatLineResponse::of).toList();
    }
}
