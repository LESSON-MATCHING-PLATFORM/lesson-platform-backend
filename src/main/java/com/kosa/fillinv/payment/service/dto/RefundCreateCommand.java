package com.kosa.fillinv.payment.service.dto;

public record RefundCreateCommand(
        String paymentId,
        String cancelReason,
        Integer refundAmount
) {
}
