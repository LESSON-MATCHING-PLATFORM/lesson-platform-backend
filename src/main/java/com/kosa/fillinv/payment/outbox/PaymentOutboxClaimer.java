package com.kosa.fillinv.payment.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentOutboxClaimer {

    private final PaymentOutboxRepository paymentOutboxRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<PaymentOutboxEvent> claim(
            String eventId,
            Collection<PaymentOutboxStatus> statuses,
            int maxRetryCount,
            Instant staleBefore,
            Instant processingStartedAt
    ) {
        int updated = paymentOutboxRepository.claimPublishableEvent(
                eventId,
                statuses,
                maxRetryCount,
                staleBefore,
                processingStartedAt
        );

        if (updated == 0) {
            return Optional.empty();
        }

        return paymentOutboxRepository.findById(eventId);
    }
}
