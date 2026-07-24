package com.kosa.fillinv.payment.service;

import com.kosa.fillinv.payment.domain.PaymentEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {

    KafkaTemplate<String, String> kafkaTemplate;

    public void publish(PaymentEvent event) {
        kafkaTemplate.send("payment-topic", event.toString());
    }

}
