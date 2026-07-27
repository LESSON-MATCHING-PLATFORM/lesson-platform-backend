package com.kosa.fillinv.payment.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentOutboxScheduler {

    private final PaymentOutboxPublisher paymentOutboxPublisher;

    @Scheduled(fixedDelayString = "${payment.outbox.publish-fixed-delay-ms:5000}")
    public void publishPaymentOutboxEvents() {
        paymentOutboxPublisher.publishReadyEvents();
    }
}
