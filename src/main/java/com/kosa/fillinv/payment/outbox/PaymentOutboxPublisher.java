package com.kosa.fillinv.payment.outbox;

import com.kosa.fillinv.payment.service.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
    private final PaymentOutboxClaimer paymentOutboxClaimer;
    private final PaymentOutboxResultUpdater paymentOutboxResultUpdater;
    private final EventPublisher eventPublisher;

    public void publishReadyEvents() {
        for (PaymentOutboxEvent event : paymentOutboxRepository.findTop100ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
                PUBLISHABLE_STATUSES,
                MAX_RETRY_COUNT
        )) {
            Optional<PaymentOutboxEvent> claimed = paymentOutboxClaimer.claim(
                    event.getId(),
                    PUBLISHABLE_STATUSES,
                    MAX_RETRY_COUNT,
                    Instant.now()
            );

            if (claimed.isEmpty()) {
                continue;
            }

            try {
                eventPublisher.publish(claimed.get());
                paymentOutboxResultUpdater.markPublished(claimed.get().getId(), Instant.now());
            } catch (Exception e) {
                paymentOutboxResultUpdater.markFailed(claimed.get().getId(), e.getMessage());
                log.error("Payment outbox publish failed. eventId={}", claimed.get().getId(), e);
            }
        }
    }
}
