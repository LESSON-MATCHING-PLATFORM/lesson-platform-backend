package com.kosa.fillinv.payment.service;

import com.kosa.fillinv.payment.domain.PaymentFailure;
import com.kosa.fillinv.payment.entity.Refund;
import com.kosa.fillinv.payment.entity.RefundHistory;
import com.kosa.fillinv.payment.entity.RefundStatus;
import com.kosa.fillinv.payment.repository.PaymentRepository;
import com.kosa.fillinv.payment.repository.RefundHistoryRepository;
import com.kosa.fillinv.payment.repository.RefundRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class RefundStatusUpdateServiceTest {

    @Autowired
    private RefundStatusUpdateService refundStatusUpdateService;

    @MockitoBean
    private PaymentRepository paymentRepository;

    @MockitoSpyBean
    private RefundRepository refundRepository;

    @Autowired
    private RefundHistoryRepository refundHistoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("결제 상태를 결제 진행중으로 변경한다.")
    void updateStatusToExecuting() {
        // given
        String refundId = "refund-001";
        String paymentKey = "payment-key";

        refundRepository.save(
                Refund.builder()
                        .id(refundId)
                        .paymentId("payment-001")
                        .paymentKey(paymentKey)
                        .orderId("order-id")
                        .refundStatus(RefundStatus.NOT_STARTED)
                        .refundAmount(1000)
                        .refundReason("refund reason")
                        .build()
        );
        entityManager.flush();
        entityManager.clear();

        // when
        Instant now = Instant.now();
        refundStatusUpdateService.updateStatusToExecuting(refundId, now);
        entityManager.flush();
        entityManager.clear();

        // then
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new AssertionError("Refund가 저장되지 않았습니다."));

        assertThat(refund.getRefundStatus()).isEqualTo(RefundStatus.EXECUTING);
        assertThat(refund.getLastAttemptedAt()).isEqualTo(now);
        assertThat(refund.getRetryCount()).isEqualTo(1);

        RefundHistory history = refundHistoryRepository.findByPaymentKey(paymentKey).getFirst();

        assertThat(history.getPreviousStatus()).isEqualTo(RefundStatus.NOT_STARTED);
        assertThat(history.getNewStatus()).isEqualTo(RefundStatus.EXECUTING);
    }

    @Test
    @DisplayName("UNKNOWN 환불은 재처리 시 다시 EXECUTING으로 변경하고 retryCount를 증가시킨다.")
    void updateStatusToExecuting_fromUnknown() {
        // given
        String refundId = "refund-001";
        String paymentKey = "payment-key";

        refundRepository.save(
                Refund.builder()
                        .id(refundId)
                        .paymentId("payment-001")
                        .paymentKey(paymentKey)
                        .orderId("order-id")
                        .refundStatus(RefundStatus.UNKNOWN)
                        .refundAmount(1000)
                        .refundReason("refund reason")
                        .build()
        );
        entityManager.flush();
        entityManager.clear();

        // when
        Instant now = Instant.now();
        refundStatusUpdateService.updateStatusToExecuting(refundId, now);
        entityManager.flush();
        entityManager.clear();

        // then
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new AssertionError("Refund가 저장되지 않았습니다."));

        assertThat(refund.getRefundStatus()).isEqualTo(RefundStatus.EXECUTING);
        assertThat(refund.getLastAttemptedAt()).isEqualTo(now);
        assertThat(refund.getRetryCount()).isEqualTo(1);

        RefundHistory history = refundHistoryRepository.findByPaymentKey(paymentKey).getFirst();

        assertThat(history.getPreviousStatus()).isEqualTo(RefundStatus.UNKNOWN);
        assertThat(history.getNewStatus()).isEqualTo(RefundStatus.EXECUTING);
    }

    @Test
    @DisplayName("결제 상태를 결제 성공으로 변경한다.")
    void updateStatusToSuccess() {
        // given
        String refundId = "refund-001";
        String paymentKey = "payment-key";
        Integer refundAmount = 1000;
        String refundReason = "refund reason";
        Instant refundedAt = Instant.now();
        String transactionKey = "transactionKey-001";
        String pspRaw = "pspRawData1101";

        refundRepository.save(
                Refund.builder()
                        .id(refundId)
                        .paymentId("payment-001")
                        .paymentKey(paymentKey)
                        .orderId("order-id")
                        .refundStatus(RefundStatus.EXECUTING)
                        .refundAmount(refundAmount)
                        .refundReason(refundReason)
                        .build()
        );
        entityManager.flush();
        entityManager.clear();

        // when
        refundStatusUpdateService.updateStatusToSuccess(
                refundId,
                transactionKey,
                refundedAt,
                pspRaw
        );
        entityManager.flush();
        entityManager.clear();

        // then
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new AssertionError("Refund가 저장되지 않았습니다."));

        assertThat(refund.getRefundStatus()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(refund.getRefundedAt()).isEqualTo(refundedAt);
        assertThat(refund.getRefundAmount()).isEqualTo(refundAmount);
        assertThat(refund.getTransactionKey()).isEqualTo(transactionKey);
        assertThat(refund.getPspRaw()).isEqualTo(pspRaw);

        RefundHistory history = refundHistoryRepository.findByPaymentKey(paymentKey).getFirst();

        assertThat(history.getPreviousStatus()).isEqualTo(RefundStatus.EXECUTING);
        assertThat(history.getNewStatus()).isEqualTo(RefundStatus.SUCCESS);
    }

    @Test
    @DisplayName("결제 상태를 결제 실패로 변경한다.")
    void updateStatusToFailure() {
        // given
        String refundId = "refund-001";
        String paymentKey = "payment-key";
        Integer refundAmount = 1000;
        String refundReason = "refund reason";
        PaymentFailure failure = new PaymentFailure("400", "결제 실패");

        refundRepository.save(
                Refund.builder()
                        .id(refundId)
                        .paymentId("payment-001")
                        .paymentKey(paymentKey)
                        .orderId("order-id")
                        .refundStatus(RefundStatus.EXECUTING)
                        .refundAmount(refundAmount)
                        .refundReason(refundReason)
                        .build()
        );
        entityManager.flush();
        entityManager.clear();

        Instant nextAttemptAt = Instant.now().plusSeconds(10);

        // when
        refundStatusUpdateService.updateStatusToFailure(
                refundId,
                failure,
                nextAttemptAt
        );
        entityManager.flush();
        entityManager.clear();

        // then
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new AssertionError("Refund가 저장되지 않았습니다."));

        assertThat(refund.getRefundStatus()).isEqualTo(RefundStatus.FAILURE);
        assertThat(refund.getNextAttemptAt()).isEqualTo(nextAttemptAt);
        assertThat(refund.getRetryCount()).isZero();

        RefundHistory history = refundHistoryRepository.findByPaymentKey(paymentKey).getFirst();

        assertThat(history.getPreviousStatus()).isEqualTo(RefundStatus.EXECUTING);
        assertThat(history.getNewStatus()).isEqualTo(RefundStatus.FAILURE);
        assertThat(history.getReason()).isEqualTo(failure.toString());
    }

    @Test
    @DisplayName("결제 상태를 알 수 없음으로 변경한다.")
    void updateStatusToUnknown() {
        // given
        String refundId = "refund-001";
        String paymentKey = "payment-key";
        Integer refundAmount = 1000;
        String refundReason = "refund reason";
        PaymentFailure failure = new PaymentFailure("400", "결제 실패");

        refundRepository.save(
                Refund.builder()
                        .id(refundId)
                        .paymentId("payment-001")
                        .paymentKey(paymentKey)
                        .orderId("order-id")
                        .refundStatus(RefundStatus.EXECUTING)
                        .refundAmount(refundAmount)
                        .refundReason(refundReason)
                        .build()
        );
        entityManager.flush();
        entityManager.clear();

        Instant nextAttemptAt = Instant.now().plusSeconds(10);

        // when
        refundStatusUpdateService.updateStatusToUnknown(refundId, failure, nextAttemptAt);
        entityManager.flush();
        entityManager.clear();

        // then
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new AssertionError("Refund가 저장되지 않았습니다."));

        assertThat(refund.getRefundStatus()).isEqualTo(RefundStatus.UNKNOWN);
        assertThat(refund.getNextAttemptAt()).isEqualTo(nextAttemptAt);
        assertThat(refund.getRetryCount()).isZero();

        RefundHistory history = refundHistoryRepository.findByPaymentKey(paymentKey).getFirst();

        assertThat(history.getPreviousStatus()).isEqualTo(RefundStatus.EXECUTING);
        assertThat(history.getNewStatus()).isEqualTo(RefundStatus.UNKNOWN);
        assertThat(history.getReason()).isEqualTo(failure.toString());
    }
}
