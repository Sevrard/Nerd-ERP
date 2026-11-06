package com.nerd.erp.api.catalog;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Indicateurs globaux du catalogue d'un workspace, indépendants de la pagination de la liste. */
public record ProductStatsResponse(
        long activeProducts,
        BigDecimal averageDayRateHt,
        BigDecimal catalogValueHt,
        Map<UUID, Long> productsByCategory
) {
}
