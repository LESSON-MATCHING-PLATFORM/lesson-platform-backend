package com.kosa.fillinv.payment.integration;

import com.kosa.fillinv.payment.outbox.PaymentOutboxEvent;
import com.kosa.fillinv.payment.outbox.PaymentOutboxPublisher;
import com.kosa.fillinv.payment.outbox.PaymentOutboxRepository;
import com.kosa.fillinv.payment.outbox.PaymentOutboxStatus;
import com.kosa.fillinv.payment.service.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:payment-outbox-publisher-concurrency-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "TOSS_SECRET_KEY=test-secret"
})
class PaymentOutboxPublisherConcurrencyIntegrationTest {

    @MockitoBean
    private EventPublisher eventPublisher;

    @Autowired
    private PaymentOutboxPublisher paymentOutboxPublisher;

    @Autowired
    private PaymentOutboxRepository paymentOutboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        paymentOutboxRepository.deleteAll();
    }

    @Test
    @DisplayName("두 publisher가 동시에 실행되어도 같은 Outbox 이벤트는 한 번만 발행한다")
    void publishReadyEvents_whenTwoWorkersRunConcurrently_publishesSameEventOnce() throws Exception {
        PaymentOutboxEvent event = paymentOutboxRepository.save(outboxEvent("event-001"));
        CountDownLatch publishStarted = new CountDownLatch(1);
        CountDownLatch releasePublish = new CountDownLatch(1);
        doAnswer(invocation -> {
            publishStarted.countDown();
            assertThat(releasePublish.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(eventPublisher).publish(any());

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executorService.submit(paymentOutboxPublisher::publishReadyEvents);
            assertThat(publishStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> second = executorService.submit(paymentOutboxPublisher::publishReadyEvents);

            releasePublish.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            releasePublish.countDown();
            executorService.shutdownNow();
            assertThat(executorService.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
        }

        verify(eventPublisher, times(1)).publish(any());
        PaymentOutboxEvent saved = paymentOutboxRepository.findById(event.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PaymentOutboxStatus.PUBLISHED);
    }

    @Test
    @DisplayName("오래된 PROCESSING Outbox 이벤트는 다음 publisher 실행에서 다시 발행된다")
    void publishReadyEvents_whenProcessingClaimIsExpired_republishesEvent() {
        PaymentOutboxEvent event = paymentOutboxRepository.save(outboxEvent("event-001"));
        jdbcTemplate.update(
                "update payment_outbox set status = ?, processing_started_at = ? where event_id = ?",
                PaymentOutboxStatus.PROCESSING.name(),
                Timestamp.from(Instant.now().minusSeconds(660)),
                event.getId()
        );

        paymentOutboxPublisher.publishReadyEvents();

        verify(eventPublisher, times(1)).publish(any());
        PaymentOutboxEvent saved = paymentOutboxRepository.findById(event.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PaymentOutboxStatus.PUBLISHED);
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
