package com.kosa.fillinv.payment.domain;

import com.kosa.fillinv.payment.entity.PaymentStatus;
import com.kosa.fillinv.payment.entity.RefundStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
public class PSPConfirmationException extends RuntimeException {
    private final String errorCode;
    private final String errorMessage;
    private final boolean isSuccess;
    private final boolean isFailure;
    private final boolean isUnknown;
    private final boolean isRetryable;

    public PSPConfirmationException(
            String errorCode,
            String errorMessage,
            boolean isSuccess,
            boolean isFailure,
            boolean isUnknown,
            boolean isRetryable
    ) {
        this(errorCode, errorMessage, isSuccess, isFailure, isUnknown, isRetryable, null);
    }

    @Builder
    public PSPConfirmationException(
            String errorCode,
            String errorMessage,
            boolean isSuccess,
            boolean isFailure,
            boolean isUnknown,
            boolean isRetryable,
            Throwable cause
    ) {
        super(errorMessage, cause);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.isSuccess = isSuccess;
        this.isFailure = isFailure;
        this.isUnknown = isUnknown;
        this.isRetryable = isRetryable;

        int trueCount = 0;
        if (isSuccess) trueCount++;
        if (isFailure) trueCount++;
        if (isUnknown) trueCount++;

        if (trueCount != 1) {
            throw new IllegalArgumentException(this.getClass().getSimpleName() + "는 올바르지 않은 결제 상태입니다.");
        }
    }

    public PaymentStatus paymentStatus() {
        if (isSuccess) return PaymentStatus.SUCCESS;
        else if (isFailure) return PaymentStatus.FAILURE;
        else if (isUnknown) return PaymentStatus.UNKNOWN;

        throw new IllegalArgumentException(this.getClass().getSimpleName() + "는 올바르지 않은 결제 상태입니다.");
    }

    public RefundStatus refundStatus() {
        if (isSuccess) return RefundStatus.SUCCESS;
        else if (isFailure) return RefundStatus.FAILURE;
        else if (isUnknown) return RefundStatus.UNKNOWN;

        throw new IllegalArgumentException(this.getClass().getSimpleName() + "는 올바르지 않은 결제 상태입니다.");
    }
}

