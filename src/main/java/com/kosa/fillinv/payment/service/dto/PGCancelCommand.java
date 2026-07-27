package com.kosa.fillinv.payment.service.dto;

public record PGCancelCommand(
        String refundId,
        String paymentKey,
        String orderId,
        String reason,
        Integer amount
) {
}
