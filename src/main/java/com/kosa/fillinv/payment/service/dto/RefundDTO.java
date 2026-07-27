package com.kosa.fillinv.payment.service.dto;

import com.kosa.fillinv.payment.entity.Refund;
import com.kosa.fillinv.payment.entity.RefundStatus;

import java.time.Instant;

public record RefundDTO(
        String refundId,
        String paymentId,
        String paymentKey,
        String orderId,
        RefundStatus refundStatus,
        Integer refundAmount,
        String refundReason,
        String transactionKey,
        Instant refundedAt,
        Instant createdAt,
        String pspRaw
) {
    public static RefundDTO of(Refund refund) {
        return new RefundDTO(
                refund.getId(),
                refund.getPaymentId(),
                refund.getPaymentKey(),
                refund.getOrderId(),
                refund.getRefundStatus(),
                refund.getRefundAmount(),
                refund.getRefundReason(),
                refund.getTransactionKey(),
                refund.getRefundedAt(),
                refund.getCreatedAt(),
                refund.getPspRaw()
        );
    }
}
