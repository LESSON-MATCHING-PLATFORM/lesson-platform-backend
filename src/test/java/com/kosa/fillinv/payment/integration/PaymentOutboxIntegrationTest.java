package com.kosa.fillinv.payment.integration;

import com.kosa.fillinv.payment.client.TossPaymentClient;
import com.kosa.fillinv.payment.domain.PSPConfirmationStatus;
import com.kosa.fillinv.payment.domain.PaymentExecutionResult;
import com.kosa.fillinv.payment.domain.PaymentExtraDetails;
import com.kosa.fillinv.payment.domain.PaymentMethod;
import com.kosa.fillinv.payment.domain.PaymentType;
import com.kosa.fillinv.payment.entity.Payment;
import com.kosa.fillinv.payment.entity.PaymentHistory;
import com.kosa.fillinv.payment.entity.PaymentStatus;
import com.kosa.fillinv.payment.outbox.PaymentOutboxEvent;
import com.kosa.fillinv.payment.outbox.PaymentOutboxRepository;
import com.kosa.fillinv.payment.outbox.PaymentOutboxStatus;
import com.kosa.fillinv.payment.repository.PaymentHistoryRepository;
import com.kosa.fillinv.payment.repository.PaymentRepository;
import com.kosa.fillinv.payment.service.PaymentService;
import com.kosa.fillinv.payment.service.dto.PaymentConfirmCommand;
import com.kosa.fillinv.payment.service.dto.PaymentConfirmResult;
import com.kosa.fillinv.schedule.service.ScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:payment-outbox-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "TOSS_SECRET_KEY=test-secret"
})
class PaymentOutboxIntegrationTest {

    @MockitoBean
    private TossPaymentClient tossPaymentClient;

    @MockitoBean
    private ScheduleService scheduleService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentHistoryRepository paymentHistoryRepository;

    @Autowired
    private PaymentOutboxRepository paymentOutboxRepository;

    @BeforeEach
    void setUp() {
        paymentOutboxRepository.deleteAll();
        paymentHistoryRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    @DisplayName("결제 승인 성공 시 Payment SUCCESS와 Outbox READY가 실제 DB에 함께 저장된다")
    void confirmSuccess_savesPaymentSuccessAndOutboxReady() {
        Payment payment = paymentRepository.save(payment());
        PaymentConfirmCommand command = confirmCommand();
        given(tossPaymentClient.confirm(command))
                .willReturn(successResult(command));

        PaymentConfirmResult result = paymentService.confirm(command);

        Payment savedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        List<PaymentHistory> histories = paymentHistoryRepository.findAllByPaymentId(payment.getId());
        List<PaymentOutboxEvent> outboxEvents = paymentOutboxRepository.findAll();

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(savedPayment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(savedPayment.getPaymentKey()).isEqualTo(command.paymentKey());
        assertThat(histories).extracting(PaymentHistory::getNewStatus)
                .containsExactly(PaymentStatus.EXECUTING, PaymentStatus.SUCCESS);

        assertThat(outboxEvents).hasSize(1);
        PaymentOutboxEvent outboxEvent = outboxEvents.getFirst();
        assertThat(outboxEvent.getStatus()).isEqualTo(PaymentOutboxStatus.READY);
        assertThat(outboxEvent.getEventType()).isEqualTo("PAYMENT_COMPLETED");
        assertThat(outboxEvent.getAggregateId()).isEqualTo(command.orderId());
        assertThat(outboxEvent.getTopic()).isEqualTo("payment-topic");
        assertThat(outboxEvent.getPayload()).contains("\"order_id\":\"schedule-001\"");
        assertThat(outboxEvent.getPayload()).contains("\"action\":\"PAYMENT_COMPLETED\"");
    }

    private Payment payment() {
        return Payment.builder()
                .id("payment-001")
                .buyerId("mentee-001")
                .sellerId("mentor-001")
                .orderId("schedule-001")
                .orderName("자바 멘토링 - 30분")
                .amount(30000)
                .build();
    }

    private PaymentConfirmCommand confirmCommand() {
        return new PaymentConfirmCommand("payment-key-001", "schedule-001", 30000);
    }

    private PaymentExecutionResult successResult(PaymentConfirmCommand command) {
        return new PaymentExecutionResult(
                command.paymentKey(),
                command.orderId(),
                new PaymentExtraDetails(
                        PaymentType.NORMAL,
                        PaymentMethod.EASY_PAY,
                        Instant.parse("2026-07-24T05:00:00Z"),
                        "자바 멘토링 - 30분",
                        PSPConfirmationStatus.DONE,
                        command.amount().longValue(),
                        "raw"
                )
        );
    }
}
