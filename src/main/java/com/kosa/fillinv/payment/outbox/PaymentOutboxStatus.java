package com.kosa.fillinv.payment.outbox;

public enum PaymentOutboxStatus {
    READY,
    PROCESSING,
    PUBLISHED,
    FAILED
}
