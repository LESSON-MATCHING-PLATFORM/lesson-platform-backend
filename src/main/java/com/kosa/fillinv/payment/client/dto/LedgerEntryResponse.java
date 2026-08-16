package com.kosa.fillinv.payment.client.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerEntryResponse(
        String entryId,
        String idempotencyKey,
        String transactionType,
        String transactionId,
        String orderId,
        String userId,
        String accountId,
        BigDecimal amount,
        String currency,
        String direction,
        String status,
        String description,
        Instant createdAt,
        String reversedEntryId,
        Long version
) {
}
