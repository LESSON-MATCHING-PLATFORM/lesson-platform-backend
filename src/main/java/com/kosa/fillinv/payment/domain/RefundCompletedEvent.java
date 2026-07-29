package com.kosa.fillinv.payment.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RefundCompletedEvent(
        @JsonProperty("action")
        String action,

        @JsonProperty("refund_id")
        String refundId,

        @JsonProperty("payment_key")
        String paymentKey,

        @JsonProperty("order_id")
        String orderId,

        @JsonProperty("amount")
        String amount,

        @JsonProperty("transaction_key")
        String transactionKey,

        @JsonProperty("refunded_at")
        String refundedAt,

        @JsonProperty("reason")
        String reason
) {
}
