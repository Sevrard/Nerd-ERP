package com.nerd.erp.infrastructure.etsy;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class EtsyMoneyParser {
    private EtsyMoneyParser() {
    }

    public static BigDecimal toMajorUnits(JsonNode moneyNode) {
        if (moneyNode == null || moneyNode.isNull() || moneyNode.isMissingNode()) {
            return BigDecimal.ZERO;
        }
        long amount = moneyNode.path("amount").asLong(0);
        int divisor = moneyNode.path("divisor").asInt(100);
        if (divisor <= 0) {
            divisor = 100;
        }
        return BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(divisor), 4, RoundingMode.HALF_UP);
    }
}
