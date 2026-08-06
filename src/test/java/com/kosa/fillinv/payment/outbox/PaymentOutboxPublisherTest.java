package com.kosa.fillinv.payment.outbox;

import com.kosa.fillinv.payment.service.EventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentOutboxPublisherTest {

    @Mock
    private PaymentOutboxRepository paymentOutboxRepository;

    @Mock
    private PaymentOutboxClaimer paymentOutboxClaimer;

    @Mock
    private PaymentOutboxResultUpdater paymentOutboxResultUpdater;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private PaymentOutboxPublisher paymentOutboxPublisher;

    @Test
    @DisplayName("READY Outbox 이벤트 발행 성공 시 PUBLISHED로 변경한다")
    void publishReadyEvents_marksPublishedOnSuccess() {
        PaymentOutboxEvent event = outboxEvent("event-001");
        givenPublishableEvents(event);
        givenClaimedEvent(event);

        paymentOutboxPublisher.publishReadyEvents();

        verify(eventPublisher).publish(event);
        verify(paymentOutboxResultUpdater).markPublished(eq(event.getId()), any());
    }

    @Test
    @DisplayName("FAILED Outbox 이벤트도 최대 재시도 횟수 전이면 다시 발행한다")
    void publishReadyEvents_retriesFailedEventBeforeMaxRetryCount() {
        PaymentOutboxEvent event = outboxEvent("event-001");
        event.markFailed("previous failure");
        givenPublishableEvents(event);
        givenClaimedEvent(event);

        paymentOutboxPublisher.publishReadyEvents();

        verify(eventPublisher).publish(event);
        assertThat(event.getRetryCount()).isEqualTo(1);
        verify(paymentOutboxResultUpdater).markPublished(eq(event.getId()), any());
    }

    @Test
    @DisplayName("READY Outbox 이벤트 발행 실패 시 FAILED로 변경하고 retryCount를 증가시킨다")
    void publishReadyEvents_marksFailedOnError() {
        PaymentOutboxEvent event = outboxEvent("event-001");
        givenPublishableEvents(event);
        givenClaimedEvent(event);
        doThrow(new IllegalStateException("kafka down"))
                .when(eventPublisher)
                .publish(event);

        paymentOutboxPublisher.publishReadyEvents();

        verify(paymentOutboxResultUpdater).markFailed(eq(event.getId()), eq("kafka down"));
    }

    @Test
    @DisplayName("Outbox publisher는 READY와 FAILED 중 retryCount가 3 미만인 이벤트만 조회한다")
    void publishReadyEvents_queriesPublishableEventsBelowMaxRetryCount() {
        PaymentOutboxEvent event = outboxEvent("event-001");
        given(paymentOutboxRepository.findTop100ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
                List.of(PaymentOutboxStatus.READY, PaymentOutboxStatus.FAILED),
                3
        ))
                .willReturn(List.of(event));
        givenClaimedEvent(event);

        paymentOutboxPublisher.publishReadyEvents();

        verify(paymentOutboxRepository).findTop100ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
                List.of(PaymentOutboxStatus.READY, PaymentOutboxStatus.FAILED),
                3
        );
    }

    @Test
    @DisplayName("Outbox 이벤트 claim에 실패하면 발행하지 않는다")
    void publishReadyEvents_whenClaimFails_skipsPublish() {
        PaymentOutboxEvent event = outboxEvent("event-001");
        givenPublishableEvents(event);
        given(paymentOutboxClaimer.claim(
                eq(event.getId()),
                eq(List.of(PaymentOutboxStatus.READY, PaymentOutboxStatus.FAILED)),
                eq(3),
                any()
        ))
                .willReturn(Optional.empty());

        paymentOutboxPublisher.publishReadyEvents();

        verify(eventPublisher, never()).publish(any());
        verify(paymentOutboxResultUpdater, never()).markPublished(any(), any());
        verify(paymentOutboxResultUpdater, never()).markFailed(any(), any());
    }

    private void givenPublishableEvents(PaymentOutboxEvent event) {
        given(paymentOutboxRepository.findTop100ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
                List.of(PaymentOutboxStatus.READY, PaymentOutboxStatus.FAILED),
                3
        ))
                .willReturn(List.of(event));
    }

    private void givenClaimedEvent(PaymentOutboxEvent event) {
        given(paymentOutboxClaimer.claim(
                eq(event.getId()),
                eq(List.of(PaymentOutboxStatus.READY, PaymentOutboxStatus.FAILED)),
                eq(3),
                any()
        ))
                .willReturn(Optional.of(event));
    }

    private PaymentOutboxEvent outboxEvent(String id) {
        return PaymentOutboxEvent.builder()
                .id(id)
                .eventType("PAYMENT_COMPLETED")
                .aggregateId("schedule-001")
                .topic("payment-topic")
                .payload("{\"order_id\":\"schedule-001\"}")
                .build();
    }
}
