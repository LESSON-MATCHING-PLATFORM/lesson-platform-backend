package com.kosa.fillinv.payment.domain;

public record RefundExecutionResult(
        String paymentKey,
        String orderId,
        RefundExtraDetails refundExtraDetails
) {
}
