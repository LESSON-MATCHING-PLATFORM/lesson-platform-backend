package com.kosa.fillinv.payment.service.dto;

public record PaymentRefundCommand(
        String paymentId,
        String cancelReason,
        Integer refundAmount
) {
    public static RefundCreateCommand toRefundCreateCommand(PaymentRefundCommand command) {
        return new RefundCreateCommand(command.paymentId(), command.cancelReason(), command.refundAmount());
    }
}
