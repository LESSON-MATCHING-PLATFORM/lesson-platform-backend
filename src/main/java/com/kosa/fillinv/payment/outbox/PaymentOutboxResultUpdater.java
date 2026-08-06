package com.kosa.fillinv.payment.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class PaymentOutboxResultUpdater {

    private final PaymentOutboxRepository paymentOutboxRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markPublished(String eventId, Instant processingStartedAt, Instant publishedAt) {
        return paymentOutboxRepository.markPublishedIfProcessingClaim(
                eventId,
                processingStartedAt,
                publishedAt
        ) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailed(String eventId, Instant processingStartedAt, String lastError) {
        return paymentOutboxRepository.markFailedIfProcessingClaim(
                eventId,
                processingStartedAt,
                lastError
        ) == 1;
    }
}
