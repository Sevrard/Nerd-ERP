package com.nerd.erp.api.invoicescan;

import java.util.List;

public record InvoiceScanBatchPreviewResponse(
        int pageCount,
        List<InvoiceScanPageResult> pages
) {
    public record InvoiceScanPageResult(
            int pageNumber,
            InvoiceScanPreviewResponse preview,
            String errorMessage
    ) {
        public static InvoiceScanPageResult success(int pageNumber, InvoiceScanPreviewResponse preview) {
            return new InvoiceScanPageResult(pageNumber, preview, null);
        }

        public static InvoiceScanPageResult failure(int pageNumber, String errorMessage) {
            return new InvoiceScanPageResult(pageNumber, null, errorMessage);
        }
    }
}
