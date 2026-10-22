package com.nerd.erp.api.etsysync;

import java.util.UUID;

public record EtsySyncReceiptCommit(
        long receiptId, boolean importReceipt, UUID customerId, boolean createCustomerFromBuyer) {
}
