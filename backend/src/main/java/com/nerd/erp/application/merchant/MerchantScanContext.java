package com.nerd.erp.application.merchant;

import java.util.UUID;

/** Marchand choisi pour un scan PDF et prompt Gemini associé. */
public record MerchantScanContext(
        UUID merchantId,
        String merchantName,
        String merchantSlug,
        String extractionPrompt
) {
    public static MerchantScanContext none() {
        return new MerchantScanContext(null, null, null, null);
    }

    public boolean hasCustomPrompt() {
        return extractionPrompt != null && !extractionPrompt.isBlank();
    }
}
