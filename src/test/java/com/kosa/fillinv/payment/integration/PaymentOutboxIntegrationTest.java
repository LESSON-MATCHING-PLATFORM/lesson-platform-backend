package com.kosa.fillinv.payment.integration;

import com.kosa.fillinv.member.entity.Member;
import com.kosa.fillinv.member.repository.MemberRepository;
import com.kosa.fillinv.payment.client.TossPaymentClient;
import com.kosa.fillinv.payment.domain.PSPConfirmationException;
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
import com.kosa.fillinv.booking.service.BookingCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import org.springframework.web.client.ResourceAccessException;

@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:payment-outbox-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never",
        "TOSS_SECRET_KEY=test-secret"
})
class PaymentOutboxIntegrationTest {

    @MockitoBean
    private TossPaymentClient tossPaymentClient;

    @MockitoBean
    private BookingCommandService bookingCommandService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentHistoryRepository paymentHistoryRepository;

    @Autowired
    private PaymentOutboxRepository paymentOutboxRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        paymentOutboxRepository.deleteAll();
        paymentHistoryRepository.deleteAll();
        paymentRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM members");
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
        assertThat(outboxEvent.getPayload()).contains("\"user_id\":\"mentee-001\"");
        assertThat(outboxEvent.getPayload()).contains("\"user_name\":\"홍길동\"");
        assertThat(outboxEvent.getPayload()).contains("\"order_id\":\"schedule-001\"");
        assertThat(outboxEvent.getPayload()).contains("\"action\":\"PAYMENT_COMPLETED\"");
    }

    @Test
    @DisplayName("FAILURE 결제 재시도 성공 시 Payment SUCCESS와 이력이 실제 DB에 저장된다")
    void confirmFailureRetrySuccess_savesPaymentSuccessAndHistories() {
        Payment payment = paymentRepository.save(failurePayment());
        PaymentConfirmCommand command = confirmCommand("payment-key-retry");
        given(tossPaymentClient.confirm(command))
                .willReturn(successResult(command));

        PaymentConfirmResult result = paymentService.confirm(command);

        Payment savedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        List<PaymentHistory> histories = paymentHistoryRepository.findAllByPaymentId(payment.getId());
        List<PaymentOutboxEvent> outboxEvents = paymentOutboxRepository.findAll();

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(savedPayment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(savedPayment.getPaymentKey()).isEqualTo(command.paymentKey());

        assertThat(histories).extracting(PaymentHistory::getPreviousStatus)
                .containsExactly(PaymentStatus.FAILURE, PaymentStatus.EXECUTING);
        assertThat(histories).extracting(PaymentHistory::getNewStatus)
                .containsExactly(PaymentStatus.EXECUTING, PaymentStatus.SUCCESS);

        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.getFirst().getEventType()).isEqualTo("PAYMENT_COMPLETED");
    }

    @Test
    @DisplayName("Toss 명확한 실패 시 Payment FAILURE와 실패 이력이 실제 DB에 저장되고 Outbox는 저장하지 않는다")
    void confirmFailure_savesPaymentFailureAndHistoryWithoutOutbox() {
        Payment payment = paymentRepository.save(payment());
        PaymentConfirmCommand command = confirmCommand();
        given(tossPaymentClient.confirm(command))
                .willThrow(pspFailure());

        PaymentConfirmResult result = paymentService.confirm(command);

        Payment savedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        List<PaymentHistory> histories = paymentHistoryRepository.findAllByPaymentId(payment.getId());
        List<PaymentOutboxEvent> outboxEvents = paymentOutboxRepository.findAll();

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILURE);
        assertThat(result.failure().errorCode()).isEqualTo("REJECT_CARD_PAYMENT");
        assertThat(savedPayment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILURE);
        assertThat(savedPayment.getPaymentKey()).isEqualTo(command.paymentKey());
        assertThat(histories).extracting(PaymentHistory::getPreviousStatus)
                .containsExactly(PaymentStatus.NOT_STARTED, PaymentStatus.EXECUTING);
        assertThat(histories).extracting(PaymentHistory::getNewStatus)
                .containsExactly(PaymentStatus.EXECUTING, PaymentStatus.FAILURE);
        assertThat(outboxEvents).isEmpty();
    }

    @Test
    @DisplayName("Toss 타임아웃 시 Payment UNKNOWN과 불명확 이력이 실제 DB에 저장되고 Outbox는 저장하지 않는다")
    void confirmTimeout_savesPaymentUnknownAndHistoryWithoutOutbox() {
        Payment payment = paymentRepository.save(payment());
        PaymentConfirmCommand command = confirmCommand();
        given(tossPaymentClient.confirm(command))
                .willThrow(new ResourceAccessException("timeout"));

        PaymentConfirmResult result = paymentService.confirm(command);

        Payment savedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        List<PaymentHistory> histories = paymentHistoryRepository.findAllByPaymentId(payment.getId());
        List<PaymentOutboxEvent> outboxEvents = paymentOutboxRepository.findAll();

        assertThat(result.status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(result.failure().errorCode()).isEqualTo("ResourceAccessException");
        assertThat(savedPayment.getPaymentStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(savedPayment.getPaymentKey()).isEqualTo(command.paymentKey());
        assertThat(histories).extracting(PaymentHistory::getPreviousStatus)
                .containsExactly(PaymentStatus.NOT_STARTED, PaymentStatus.EXECUTING);
        assertThat(histories).extracting(PaymentHistory::getNewStatus)
                .containsExactly(PaymentStatus.EXECUTING, PaymentStatus.UNKNOWN);
        assertThat(outboxEvents).isEmpty();
    }

    @Test
    @DisplayName("중복 confirm 요청은 Toss 호출과 PaymentHistory, Outbox 저장을 중복 수행하지 않는다")
    void confirmDuplicateRequest_claimsExecutionOnlyOnce() throws Exception {
        Payment payment = paymentRepository.save(payment());
        PaymentConfirmCommand command = confirmCommand();
        CountDownLatch tossStarted = new CountDownLatch(1);
        CountDownLatch releaseToss = new CountDownLatch(1);
        given(tossPaymentClient.confirm(command))
                .willAnswer(invocation -> {
                    tossStarted.countDown();
                    assertThat(releaseToss.await(5, TimeUnit.SECONDS)).isTrue();
                    return successResult(command);
                });

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<PaymentConfirmResult> firstResult = executorService.submit(() -> paymentService.confirm(command));
            assertThat(tossStarted.await(5, TimeUnit.SECONDS)).isTrue();

            PaymentConfirmResult duplicateResult = paymentService.confirm(command);

            assertThat(duplicateResult.status()).isEqualTo(PaymentStatus.EXECUTING);
            assertThat(duplicateResult.failure()).isNull();

            releaseToss.countDown();
            assertThat(firstResult.get(5, TimeUnit.SECONDS).status()).isEqualTo(PaymentStatus.SUCCESS);
        } finally {
            releaseToss.countDown();
            executorService.shutdownNow();
            assertThat(executorService.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
        }

        Payment savedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        List<PaymentHistory> histories = paymentHistoryRepository.findAllByPaymentId(payment.getId());
        List<PaymentOutboxEvent> outboxEvents = paymentOutboxRepository.findAll();

        assertThat(savedPayment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(histories).extracting(PaymentHistory::getNewStatus)
                .containsExactly(PaymentStatus.EXECUTING, PaymentStatus.SUCCESS);
        assertThat(outboxEvents).hasSize(1);
        verify(tossPaymentClient, times(1)).confirm(command);
    }

    private Payment payment() {
        memberRepository.save(mentee());
        return Payment.builder()
                .id("payment-001")
                .buyerId("mentee-001")
                .sellerId("mentor-001")
                .orderId("schedule-001")
                .orderName("자바 멘토링 - 30분")
                .amount(30000)
                .build();
    }

    private Member mentee() {
        return Member.builder()
                .id("mentee-001")
                .nickname("홍길동")
                .phoneNum("01012345678")
                .email("mentee@example.com")
                .password("password")
                .build();
    }

    private Payment failurePayment() {
        Payment payment = payment();
        payment.markExecuting();
        payment.markFail();
        return payment;
    }

    private PaymentConfirmCommand confirmCommand() {
        return confirmCommand("payment-key-001");
    }

    private PaymentConfirmCommand confirmCommand(String paymentKey) {
        return new PaymentConfirmCommand(paymentKey, "schedule-001", 30000);
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

    private PSPConfirmationException pspFailure() {
        return new PSPConfirmationException(
                "REJECT_CARD_PAYMENT",
                "잔액 부족",
                false,
                true,
                false,
                false
        );
    }
}
