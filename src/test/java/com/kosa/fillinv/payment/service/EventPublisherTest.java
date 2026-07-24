package com.kosa.fillinv.payment.service;

import com.kosa.fillinv.payment.outbox.PaymentOutboxEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private EventPublisher eventPublisher;

    @Test
    @DisplayName("Outbox 이벤트 payload를 Kafka 메시지로 발행한다")
    void publish_sendsOutboxEventPayload() {
        PaymentOutboxEvent event = outboxEvent();

        eventPublisher.publish(event);

        ProducerRecord<String, String> record = sentRecord();
        assertThat(record.topic()).isEqualTo(event.getTopic());
        assertThat(record.key()).isEqualTo(event.getAggregateId());
        assertThat(record.value()).isEqualTo(event.getPayload());
        assertThat(new String(record.headers().lastHeader("eventId").value(), StandardCharsets.UTF_8))
                .isEqualTo(event.getId());
    }

    private ProducerRecord<String, String> sentRecord() {
        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        return captor.getValue();
    }

    private PaymentOutboxEvent outboxEvent() {
        return PaymentOutboxEvent.builder()
                .id("event-001")
                .eventType("PAYMENT_COMPLETED")
                .aggregateId("schedule-001")
                .topic("payment-topic")
                .payload("{\"order_id\":\"schedule-001\"}")
                .build();
    }
}
