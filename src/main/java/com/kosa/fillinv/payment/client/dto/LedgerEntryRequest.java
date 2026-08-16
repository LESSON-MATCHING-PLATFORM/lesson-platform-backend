package com.kosa.fillinv.payment.client.dto;

import java.math.BigDecimal;

public record LedgerEntryRequest(
        String idempotencyKey,
        String transactionType,
        String transactionId,
        String orderId,
        String userId,
        String accountId,
        BigDecimal amount,
        String currency,
        String direction,
        String description
) {
}
