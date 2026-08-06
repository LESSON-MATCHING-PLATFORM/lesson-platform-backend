package com.kosa.fillinv.payment.service;

import com.kosa.fillinv.payment.client.TossPaymentClient;
import com.kosa.fillinv.payment.domain.PSPConfirmationException;
import com.kosa.fillinv.payment.domain.RefundExecutionResult;
import com.kosa.fillinv.payment.domain.RefundExtraDetails;
import com.kosa.fillinv.payment.entity.RefundStatus;
import com.kosa.fillinv.payment.outbox.PaymentOutboxService;
import com.kosa.fillinv.payment.repository.RefundRepository;
import com.kosa.fillinv.payment.service.dto.PGCancelCommand;
import com.kosa.fillinv.payment.service.dto.PaymentRefundResult;
import com.kosa.fillinv.booking.service.BookingCommandService;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundProcessorTest {

    @Mock
    private RefundStatusUpdateService refundStatusUpdateService;

    @Mock
    private TossPaymentClient tossPaymentClient;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private RefundRetryBackoffPolicy refundRetryBackoffPolicy;

    @Mock
    private PaymentOutboxService paymentOutboxService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private BookingCommandService bookingCommandService;

    @InjectMocks
    private RefundProcessor refundProcessor;

    private final Instant nextAttemptAt = Instant.parse("2026-07-27T00:01:00Z");

    @BeforeEach
    void setUpTransactionTemplate() {
        lenient().doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        lenient().when(refundStatusUpdateService.tryUpdateStatusToExecuting(any(), any()))
                .thenReturn(true);
    }

    @Test
    @DisplayName("환불 PG 취소 성공 시 환불 성공 상태와 완료 이벤트를 저장한다")
    void processPGCancel_whenPgCancelSucceeds() {
        PGCancelCommand command = command();
        when(tossPaymentClient.cancel(any()))
                .thenReturn(successResult());

        PaymentRefundResult result = refundProcessor.processPGCancel(command);

        assertThat(result.status()).isEqualTo(RefundStatus.SUCCESS);
        verify(refundStatusUpdateService).tryUpdateStatusToExecuting(eq(command.refundId()), any());
        verify(tossPaymentClient).cancel(any());
        verify(refundStatusUpdateService).updateStatusToSuccess(
                eq(command.refundId()),
                eq("transaction-key"),
                eq(Instant.parse("2026-07-27T00:00:00Z")),
                eq("raw")
        );
        verify(paymentOutboxService).saveRefundCompletedEvent(eq(command), any());
        verify(bookingCommandService).cancelByRefund(command.orderId());
    }

    @Test
    @DisplayName("환불 성공 후 Booking 취소 실패는 환불 성공 결과에 영향을 주지 않는다")
    void processPGCancel_whenBookingCancellationFails_keepsRefundSuccess() {
        PGCancelCommand command = command();
        when(tossPaymentClient.cancel(any()))
                .thenReturn(successResult());
        doThrow(new IllegalStateException("booking cancel failed"))
                .when(bookingCommandService)
                .cancelByRefund(command.orderId());

        PaymentRefundResult result = refundProcessor.processPGCancel(command);

        assertThat(result.status()).isEqualTo(RefundStatus.SUCCESS);
        verify(refundStatusUpdateService).updateStatusToSuccess(
                eq(command.refundId()),
                eq("transaction-key"),
                eq(Instant.parse("2026-07-27T00:00:00Z")),
                eq("raw")
        );
        verify(paymentOutboxService).saveRefundCompletedEvent(eq(command), any());
        verify(bookingCommandService).cancelByRefund(command.orderId());
        verify(refundStatusUpdateService, never()).updateStatusToFailure(any(), any(), any());
        verify(refundStatusUpdateService, never()).updateStatusToUnknown(any(), any(), any());
    }

    @Test
    @DisplayName("환불 PG 취소가 명확히 실패하면 환불을 실패 상태로 변경하고 완료 이벤트는 저장하지 않는다")
    void processPGCancel_whenPgCancelFails() {
        PGCancelCommand command = command();
        when(tossPaymentClient.cancel(any()))
                .thenThrow(pspFailure());
        when(refundRepository.getRetryCountByRefundId(command.refundId()))
                .thenReturn(1);
        when(refundRetryBackoffPolicy.nextAttemptAt(any(), eq(1)))
                .thenReturn(nextAttemptAt);

        PaymentRefundResult result = refundProcessor.processPGCancel(command);

        assertThat(result.status()).isEqualTo(RefundStatus.FAILURE);
        verify(refundStatusUpdateService).tryUpdateStatusToExecuting(eq(command.refundId()), any());
        verify(refundStatusUpdateService).updateStatusToFailure(eq(command.refundId()), any(), eq(nextAttemptAt));
        verify(paymentOutboxService, never()).saveRefundCompletedEvent(any(), any());
        verify(bookingCommandService, never()).cancelByRefund(any());
    }

    @Test
    @DisplayName("환불 PG 취소 결과가 불명확하면 환불을 UNKNOWN 상태로 변경하고 완료 이벤트는 저장하지 않는다")
    void processPGCancel_whenPgCancelUnknown() {
        PGCancelCommand command = command();
        when(tossPaymentClient.cancel(any()))
                .thenThrow(pspUnknown());
        when(refundRepository.getRetryCountByRefundId(command.refundId()))
                .thenReturn(1);
        when(refundRetryBackoffPolicy.nextAttemptAt(any(), eq(1)))
                .thenReturn(nextAttemptAt);

        PaymentRefundResult result = refundProcessor.processPGCancel(command);

        assertThat(result.status()).isEqualTo(RefundStatus.UNKNOWN);
        verify(refundStatusUpdateService).tryUpdateStatusToExecuting(eq(command.refundId()), any());
        verify(refundStatusUpdateService).updateStatusToUnknown(eq(command.refundId()), any(), eq(nextAttemptAt));
        verify(paymentOutboxService, never()).saveRefundCompletedEvent(any(), any());
        verify(bookingCommandService, never()).cancelByRefund(any());
    }

    @Test
    @DisplayName("PG 호출 전 EXECUTING claim에 실패하면 PG 호출 없이 건너뛴다")
    void processPGCancel_whenExecutingClaimFails_skipsPgCancel() {
        PGCancelCommand command = command();
        when(refundStatusUpdateService.tryUpdateStatusToExecuting(eq(command.refundId()), any()))
                .thenReturn(false);

        PaymentRefundResult result = refundProcessor.processPGCancel(command);

        assertThat(result.status()).isEqualTo(RefundStatus.EXECUTING);
        verify(tossPaymentClient, never()).cancel(any());
        verify(refundStatusUpdateService, never()).updateStatusToFailure(any(), any(), any());
        verify(refundStatusUpdateService, never()).updateStatusToUnknown(any(), any(), any());
    }

    @Test
    @DisplayName("외부 연결 오류는 UNKNOWN으로 분류하고 nextAttemptAt을 저장한다")
    void processPGCancel_whenResourceAccessException() {
        assertUnknownError(new ResourceAccessException("network down"));
    }

    @Test
    @DisplayName("Spring DataAccessException은 UNKNOWN으로 분류하고 nextAttemptAt을 저장한다")
    void processPGCancel_whenDataAccessException() {
        assertUnknownError(new DataAccessResourceFailureException("db down"));
    }

    @Test
    @DisplayName("Spring TransactionException은 UNKNOWN으로 분류하고 nextAttemptAt을 저장한다")
    void processPGCancel_whenTransactionException() {
        assertUnknownError(new TransactionSystemException("tx failed"));
    }

    @Test
    @DisplayName("JPA PersistenceException은 UNKNOWN으로 분류하고 nextAttemptAt을 저장한다")
    void processPGCancel_whenPersistenceException() {
        assertUnknownError(new PersistenceException("jpa failed"));
    }

    @Test
    @DisplayName("분류되지 않은 일반 예외는 FAILURE로 분류하고 nextAttemptAt을 저장한다")
    void processPGCancel_whenUnexpectedException() {
        PGCancelCommand command = command();
        when(tossPaymentClient.cancel(any()))
                .thenThrow(new IllegalStateException("boom"));
        when(refundRepository.getRetryCountByRefundId(command.refundId()))
                .thenReturn(2);
        when(refundRetryBackoffPolicy.nextAttemptAt(any(), eq(2)))
                .thenReturn(nextAttemptAt);

        PaymentRefundResult result = refundProcessor.processPGCancel(command);

        assertThat(result.status()).isEqualTo(RefundStatus.FAILURE);
        verify(refundStatusUpdateService).updateStatusToFailure(eq(command.refundId()), any(), eq(nextAttemptAt));
        verify(paymentOutboxService, never()).saveRefundCompletedEvent(any(), any());
    }

    private PGCancelCommand command() {
        return new PGCancelCommand("refund-001", "payment-key", "order-001", "단순 변심", 1000);
    }

    private void assertUnknownError(RuntimeException exception) {
        PGCancelCommand command = command();
        when(tossPaymentClient.cancel(any()))
                .thenThrow(exception);
        when(refundRepository.getRetryCountByRefundId(command.refundId()))
                .thenReturn(2);
        when(refundRetryBackoffPolicy.nextAttemptAt(any(), eq(2)))
                .thenReturn(nextAttemptAt);

        PaymentRefundResult result = refundProcessor.processPGCancel(command);

        assertThat(result.status()).isEqualTo(RefundStatus.UNKNOWN);
        verify(refundStatusUpdateService).updateStatusToUnknown(eq(command.refundId()), any(), eq(nextAttemptAt));
        verify(paymentOutboxService, never()).saveRefundCompletedEvent(any(), any());
    }

    private RefundExecutionResult successResult() {
        return new RefundExecutionResult(
                "payment-key",
                "order-001",
                new RefundExtraDetails(
                        Instant.parse("2026-07-27T00:00:00Z"),
                        1000,
                        "단순 변심",
                        "transaction-key",
                        "raw"
                )
        );
    }

    private PSPConfirmationException pspFailure() {
        return PSPConfirmationException.builder()
                .errorCode("400")
                .errorMessage("실패")
                .isSuccess(false)
                .isFailure(true)
                .isUnknown(false)
                .isRetryable(false)
                .build();
    }

    private PSPConfirmationException pspUnknown() {
        return PSPConfirmationException.builder()
                .errorCode("500")
                .errorMessage("알 수 없음")
                .isSuccess(false)
                .isFailure(false)
                .isUnknown(true)
                .isRetryable(true)
                .build();
    }
}
