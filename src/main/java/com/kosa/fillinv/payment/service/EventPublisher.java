package com.kosa.fillinv.payment.service;

import com.kosa.fillinv.payment.outbox.PaymentOutboxEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EventPublisher {

    private static final String EVENT_ID_HEADER = "eventId";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void publish(PaymentOutboxEvent event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(event.getTopic(), event.getAggregateId(), event.getPayload());
        record.headers().add(EVENT_ID_HEADER, event.getId().getBytes(StandardCharsets.UTF_8));

        kafkaTemplate.send(record);
    }

}
