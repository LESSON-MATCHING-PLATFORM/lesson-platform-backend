package com.kosa.fillinv.payment.service.dto;

import com.kosa.fillinv.payment.domain.PaymentFailure;
import com.kosa.fillinv.payment.entity.RefundStatus;

public record PaymentRefundResult(
        RefundStatus status,
        PaymentFailure failure
) {
}
