package com.kosa.fillinv.payment.outbox;

import com.kosa.fillinv.payment.service.EventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentOutboxPublisherTest {

    @Mock
    private PaymentOutboxRepository paymentOutboxRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private PaymentOutboxPublisher paymentOutboxPublisher;

    @Test
    @DisplayName("READY Outbox 이벤트 발행 성공 시 PUBLISHED로 변경한다")
    void publishReadyEvents_marksPublishedOnSuccess() {
        PaymentOutboxEvent event = outboxEvent("event-001");
        given(paymentOutboxRepository.findTop100ByStatusOrderByCreatedAtAsc(PaymentOutboxStatus.READY))
                .willReturn(List.of(event));

        paymentOutboxPublisher.publishReadyEvents();

        verify(eventPublisher).publish(event);
        assertThat(event.getStatus()).isEqualTo(PaymentOutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getLastError()).isNull();
    }

    @Test
    @DisplayName("READY Outbox 이벤트 발행 실패 시 FAILED로 변경하고 retryCount를 증가시킨다")
    void publishReadyEvents_marksFailedOnError() {
        PaymentOutboxEvent event = outboxEvent("event-001");
        given(paymentOutboxRepository.findTop100ByStatusOrderByCreatedAtAsc(PaymentOutboxStatus.READY))
                .willReturn(List.of(event));
        doThrow(new IllegalStateException("kafka down"))
                .when(eventPublisher)
                .publish(event);

        paymentOutboxPublisher.publishReadyEvents();

        assertThat(event.getStatus()).isEqualTo(PaymentOutboxStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getLastError()).contains("kafka down");
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
