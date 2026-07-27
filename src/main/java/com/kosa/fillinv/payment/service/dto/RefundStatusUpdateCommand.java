package com.kosa.fillinv.payment.service.dto;

import com.kosa.fillinv.payment.domain.PaymentFailure;
import com.kosa.fillinv.payment.domain.RefundExtraDetails;
import com.kosa.fillinv.payment.entity.RefundStatus;

public record RefundStatusUpdateCommand(
        String refundId,
        RefundStatus status,
        RefundExtraDetails extraDetails,
        PaymentFailure failure
) {
}
