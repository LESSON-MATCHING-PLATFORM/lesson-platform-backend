package com.kosa.fillinv.payment.outbox;

import com.kosa.fillinv.payment.service.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentOutboxPublisher {

    private static final int MAX_RETRY_COUNT = 3;
    private static final List<PaymentOutboxStatus> PUBLISHABLE_STATUSES = List.of(
            PaymentOutboxStatus.READY,
            PaymentOutboxStatus.FAILED
    );

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final EventPublisher eventPublisher;

    @Transactional
    public void publishReadyEvents() {
        for (PaymentOutboxEvent event : paymentOutboxRepository.findTop100ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
                PUBLISHABLE_STATUSES,
                MAX_RETRY_COUNT
        )) {
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
