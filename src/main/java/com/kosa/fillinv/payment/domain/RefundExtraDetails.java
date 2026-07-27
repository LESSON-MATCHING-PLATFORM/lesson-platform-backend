package com.kosa.fillinv.payment.domain;

import java.time.Instant;

public record RefundExtraDetails(
        Instant refundedAt,
        Integer refundAmount,
        String refundReason,
        String transactionKey,
        String pspRawData
) {
}
