package com.kosa.fillinv.payment.client.dto;

public record PaymentCancelCommand(
        String refundId,
        String paymentKey,
        String orderId,
        String cancelReason,
        Integer refundAmount
) {
}
