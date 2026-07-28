package com.kosa.fillinv.payment.integration;

import com.kosa.fillinv.payment.client.TossPaymentClient;
import com.kosa.fillinv.payment.domain.PSPConfirmationException;
import com.kosa.fillinv.payment.domain.RefundExecutionResult;
import com.kosa.fillinv.payment.domain.RefundExtraDetails;
import com.kosa.fillinv.payment.entity.Refund;
import com.kosa.fillinv.payment.entity.RefundHistory;
import com.kosa.fillinv.payment.entity.RefundStatus;
import com.kosa.fillinv.payment.repository.RefundHistoryRepository;
import com.kosa.fillinv.payment.repository.RefundRepository;
import com.kosa.fillinv.payment.service.RefundProcessor;
import com.kosa.fillinv.payment.service.dto.PGCancelCommand;
import com.kosa.fillinv.payment.service.dto.PaymentRefundResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:refund-processor-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "TOSS_SECRET_KEY=test-secret"
})
class RefundProcessorIntegrationTest {

    @MockitoBean
    private TossPaymentClient tossPaymentClient;

    @Autowired
    private RefundProcessor refundProcessor;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private RefundHistoryRepository refundHistoryRepository;

    @BeforeEach
    void setUp() {
        refundHistoryRepository.deleteAll();
        refundRepository.deleteAll();
    }

    @Test
    @DisplayName("환불 PG 취소 성공 시 Refund SUCCESS와 이력이 실제 DB에 저장된다")
    void processPGCancel_success_savesRefundSuccessAndHistories() {
        Refund refund = refundRepository.save(refund("refund-001"));
        PGCancelCommand command = command(refund);
        given(tossPaymentClient.cancel(any()))
                .willReturn(successResult(command));

        PaymentRefundResult result = refundProcessor.processPGCancel(command);

        Refund savedRefund = refundRepository.findById(refund.getId()).orElseThrow();
        List<RefundHistory> histories = refundHistoryRepository.findByPaymentKey(refund.getPaymentKey());

        assertThat(result.status()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(savedRefund.getRefundStatus()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(savedRefund.getRetryCount()).isEqualTo(1);
        assertThat(savedRefund.getTransactionKey()).isEqualTo("transaction-key-001");
        assertThat(savedRefund.getRefundedAt()).isEqualTo(Instant.parse("2026-07-28T00:00:00Z"));
        assertThat(savedRefund.getPspRaw()).isEqualTo("raw");
        assertThat(histories).extracting(RefundHistory::getNewStatus)
                .containsExactly(RefundStatus.EXECUTING, RefundStatus.SUCCESS);
    }

    @Test
    @DisplayName("환불 PG 취소 결과가 불명확하면 Refund UNKNOWN과 다음 재시도 시간이 저장된다")
    void processPGCancel_unknown_savesRefundUnknownAndNextAttemptAt() {
        Refund refund = refundRepository.save(refund("refund-001"));
        PGCancelCommand command = command(refund);
        given(tossPaymentClient.cancel(any()))
                .willThrow(unknownException());

        PaymentRefundResult result = refundProcessor.processPGCancel(command);

        Refund savedRefund = refundRepository.findById(refund.getId()).orElseThrow();
        List<RefundHistory> histories = refundHistoryRepository.findByPaymentKey(refund.getPaymentKey());

        assertThat(result.status()).isEqualTo(RefundStatus.UNKNOWN);
        assertThat(savedRefund.getRefundStatus()).isEqualTo(RefundStatus.UNKNOWN);
        assertThat(savedRefund.getRetryCount()).isEqualTo(1);
        assertThat(savedRefund.getNextAttemptAt()).isNotNull();
        assertThat(histories).extracting(RefundHistory::getNewStatus)
                .containsExactly(RefundStatus.EXECUTING, RefundStatus.UNKNOWN);
    }

    @Test
    @DisplayName("FAILURE 환불 재시도 성공 시 다시 EXECUTING을 거쳐 SUCCESS로 변경된다")
    void processPGCancel_failureRetrySuccess_savesRefundSuccess() {
        Refund refund = refund("refund-001");
        refund.markExecuting(Instant.parse("2026-07-28T00:00:00Z"));
        refund.markFail(Instant.parse("2026-07-28T00:01:00Z"));
        refundRepository.save(refund);

        PGCancelCommand command = command(refund);
        given(tossPaymentClient.cancel(any()))
                .willReturn(successResult(command));

        PaymentRefundResult result = refundProcessor.processPGCancel(command);

        Refund savedRefund = refundRepository.findById(refund.getId()).orElseThrow();
        List<RefundHistory> histories = refundHistoryRepository.findByPaymentKey(refund.getPaymentKey());

        assertThat(result.status()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(savedRefund.getRefundStatus()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(savedRefund.getRetryCount()).isEqualTo(2);
        assertThat(histories).extracting(RefundHistory::getPreviousStatus)
                .containsExactly(RefundStatus.FAILURE, RefundStatus.EXECUTING);
        assertThat(histories).extracting(RefundHistory::getNewStatus)
                .containsExactly(RefundStatus.EXECUTING, RefundStatus.SUCCESS);
    }

    private Refund refund(String refundId) {
        return Refund.builder()
                .id(refundId)
                .paymentId("payment-001")
                .paymentKey("payment-key-001")
                .orderId("order-001")
                .refundStatus(RefundStatus.NOT_STARTED)
                .refundAmount(1000)
                .refundReason("단순 변심")
                .build();
    }

    private PGCancelCommand command(Refund refund) {
        return new PGCancelCommand(
                refund.getId(),
                refund.getPaymentKey(),
                refund.getOrderId(),
                refund.getRefundReason(),
                refund.getRefundAmount()
        );
    }

    private RefundExecutionResult successResult(PGCancelCommand command) {
        return new RefundExecutionResult(
                command.paymentKey(),
                command.orderId(),
                new RefundExtraDetails(
                        Instant.parse("2026-07-28T00:00:00Z"),
                        command.amount(),
                        command.reason(),
                        "transaction-key-001",
                        "raw"
                )
        );
    }

    private PSPConfirmationException unknownException() {
        return PSPConfirmationException.builder()
                .errorCode("500")
                .errorMessage("환불 결과를 확인할 수 없습니다.")
                .isSuccess(false)
                .isFailure(false)
                .isUnknown(true)
                .isRetryable(true)
                .build();
    }
}
