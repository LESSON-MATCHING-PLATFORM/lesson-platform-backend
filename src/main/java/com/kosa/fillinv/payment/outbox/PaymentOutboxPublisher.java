package com.kosa.fillinv.payment.outbox;

import com.kosa.fillinv.payment.service.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
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
    static final Duration PROCESSING_LEASE_DURATION = Duration.ofMinutes(10);
    private static final int BATCH_SIZE = 100;

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final PaymentOutboxClaimer paymentOutboxClaimer;
    private final PaymentOutboxResultUpdater paymentOutboxResultUpdater;
    private final EventPublisher eventPublisher;

    public void publishReadyEvents() {
        Instant now = Instant.now();
        Instant staleBefore = now.minus(PROCESSING_LEASE_DURATION);

        for (PaymentOutboxEvent event : paymentOutboxRepository.findPublishableEvents(
                PUBLISHABLE_STATUSES,
                MAX_RETRY_COUNT,
                staleBefore,
                PageRequest.of(0, BATCH_SIZE)
        )) {
            Instant processingStartedAt = Instant.now();
            Optional<PaymentOutboxEvent> claimed = paymentOutboxClaimer.claim(
                    event.getId(),
                    PUBLISHABLE_STATUSES,
                    MAX_RETRY_COUNT,
                    staleBefore,
                    processingStartedAt
            );

            if (claimed.isEmpty()) {
                continue;
            }

            if (!isCurrentProcessingClaim(claimed.get())) {
                log.info("Skip expired or reclaimed outbox claim before publish. eventId={}", claimed.get().getId());
                continue;
            }

            try {
                eventPublisher.publish(claimed.get());
                if (!paymentOutboxResultUpdater.markPublished(
                        claimed.get().getId(),
                        claimed.get().getProcessingStartedAt(),
                        Instant.now()
                )) {
                    log.info("Skip marking published because outbox claim changed. eventId={}", claimed.get().getId());
                }
            } catch (Exception e) {
                if (!paymentOutboxResultUpdater.markFailed(
                        claimed.get().getId(),
                        claimed.get().getProcessingStartedAt(),
                        e.getMessage()
                )) {
                    log.info("Skip marking failed because outbox claim changed. eventId={}", claimed.get().getId());
                }
                log.error("Payment outbox publish failed. eventId={}", claimed.get().getId(), e);
            }
        }
    }

    private boolean isCurrentProcessingClaim(PaymentOutboxEvent event) {
        return paymentOutboxRepository.existsByIdAndStatusAndProcessingStartedAt(
                event.getId(),
                PaymentOutboxStatus.PROCESSING,
                event.getProcessingStartedAt()
        );
    }
}
