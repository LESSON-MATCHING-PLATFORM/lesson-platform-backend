package com.kosa.fillinv.payment.client.dto;

public record TossPaymentCancelRequest(
        String cancelReason,
        Integer cancelAmount
) {
}
