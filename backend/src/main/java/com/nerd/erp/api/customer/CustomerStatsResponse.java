package com.nerd.erp.api.customer;

/** Compteurs globaux (hors archivés) d'un workspace, indépendants de la pagination de la liste. */
public record CustomerStatsResponse(
        long total,
        long prospects,
        long clients,
        long followUpsDue
) {
}
