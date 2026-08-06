package com.kosa.fillinv.payment.outbox;

import com.kosa.fillinv.global.exception.ResourceException;
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
    public void markPublished(String eventId, Instant publishedAt) {
        PaymentOutboxEvent event = getEvent(eventId);
        event.markPublished(publishedAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String eventId, String lastError) {
        PaymentOutboxEvent event = getEvent(eventId);
        event.markFailed(lastError);
    }

    private PaymentOutboxEvent getEvent(String eventId) {
        return paymentOutboxRepository.findById(eventId)
                .orElseThrow(() -> new ResourceException.NotFound("결제 Outbox 이벤트 없음"));
    }
}
