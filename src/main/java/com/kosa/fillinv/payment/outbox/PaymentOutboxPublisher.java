package com.kosa.fillinv.payment.outbox;

import com.kosa.fillinv.payment.service.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentOutboxPublisher {

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final EventPublisher eventPublisher;

    @Transactional
    public void publishReadyEvents() {
        for (PaymentOutboxEvent event : paymentOutboxRepository.findTop100ByStatusOrderByCreatedAtAsc(PaymentOutboxStatus.READY)) {
            try {
                eventPublisher.publish(event);
                event.markPublished(Instant.now());
            } catch (Exception e) {
                event.markFailed(e.getMessage());
                log.error("Payment outbox publish failed. eventId={}", event.getId(), e);
            }
        }
    }
}
