package com.nerd.erp.api.etsysync;

public record EtsySyncCommitResponse(
        int productsCreated, int productsUpdated, int productsSkipped,
        int receiptsImported, int receiptsSkipped) {
}
