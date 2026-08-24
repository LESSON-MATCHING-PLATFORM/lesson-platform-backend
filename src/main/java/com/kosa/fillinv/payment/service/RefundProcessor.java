package com.kosa.fillinv.payment.service;

import com.kosa.fillinv.payment.client.TossPaymentClient;
import com.kosa.fillinv.payment.client.LedgerClient;
import com.kosa.fillinv.payment.client.dto.LedgerEntryRequest;
import com.kosa.fillinv.payment.client.dto.LedgerEntryResponse;
import com.kosa.fillinv.payment.client.dto.PaymentCancelCommand;
import com.kosa.fillinv.payment.domain.PSPConfirmationException;
import com.kosa.fillinv.payment.domain.PaymentFailure;
import com.kosa.fillinv.payment.domain.RefundExecutionResult;
import com.kosa.fillinv.payment.entity.Payment;
import com.kosa.fillinv.payment.entity.Refund;
import com.kosa.fillinv.payment.entity.RefundStatus;
import com.kosa.fillinv.payment.outbox.PaymentOutboxService;
import com.kosa.fillinv.payment.repository.PaymentRepository;
import com.kosa.fillinv.payment.repository.RefundRepository;
import com.kosa.fillinv.payment.service.dto.PGCancelCommand;
import com.kosa.fillinv.payment.service.dto.PaymentRefundResult;
import com.kosa.fillinv.booking.service.BookingCommandService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundProcessor {

    private final RefundStatusUpdateService refundStatusUpdateService;
    private final TossPaymentClient tossPaymentClient;
    private final LedgerClient ledgerClient;
    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRetryBackoffPolicy refundRetryBackoffPolicy;
    private final PaymentOutboxService paymentOutboxService;
    private final TransactionTemplate transactionTemplate;
    private final BookingCommandService bookingCommandService;

    public PaymentRefundResult processPGCancel(PGCancelCommand command) {
        boolean claimed = refundStatusUpdateService.tryUpdateStatusToExecuting(command.refundId(), Instant.now());
        if (!claimed) {
            log.info("Skip refund processing because refund is already claimed or completed. refundId={}", command.refundId());
            return new PaymentRefundResult(currentRefundStatus(command.refundId()), null);
        }

        try {
            RefundExecutionResult result = tossPaymentClient.cancel(
                    new PaymentCancelCommand(
                            command.refundId(),
                            command.paymentKey(),
                            command.orderId(),
                            command.reason(),
                            command.amount()
                    ));

            recordRefundLedger(command, result);

            transactionTemplate.executeWithoutResult(status -> {
                refundStatusUpdateService.updateStatusToSuccess(
                        command.refundId(),
                        result.refundExtraDetails().transactionKey(),
                        result.refundExtraDetails().refundedAt(),
                        result.refundExtraDetails().pspRawData()
                );
                paymentOutboxService.saveRefundCompletedEvent(command, result);
            });

            cancelBookingAfterRefund(command.orderId());

            return new PaymentRefundResult(RefundStatus.SUCCESS, null);
        } catch (Exception e) {
            return handlePGCancelError(command.refundId(), e);
        }
    }

    private void cancelBookingAfterRefund(String orderId) {
        try {
            bookingCommandService.cancelByRefund(orderId);
        } catch (Exception e) {
            log.error("Refund succeeded, but booking cancellation failed. orderId={}", orderId, e);
        }
    }

    public PaymentRefundResult handlePGCancelError(String refundId, Throwable e) {
        RefundStatus status;
        PaymentFailure failure;

        if (e instanceof PSPConfirmationException) {
            status = ((PSPConfirmationException) e).refundStatus();
            failure = new PaymentFailure(((PSPConfirmationException) e).getErrorCode(), e.getMessage());
        } else if (isDatabaseError(e)) {
            status = RefundStatus.UNKNOWN;
            failure = new PaymentFailure(e.getClass().getSimpleName(), e.getMessage() == null ? "환불 실행 도중 데이터베이스 관련 오류 발생" : e.getMessage());
        } else if (e instanceof LedgerRefundRecordingException) {
            status = RefundStatus.UNKNOWN;
            failure = new PaymentFailure(e.getClass().getSimpleName(), e.getMessage() == null ? "환불 원장 기록 중 오류 발생" : e.getMessage());
        } else if (e instanceof ResourceAccessException) {
            status = RefundStatus.UNKNOWN;
            failure = new PaymentFailure(e.getClass().getSimpleName(), e.getMessage() == null ? "환불 실행 도중 외부 연결 오류 발생" : e.getMessage());
        } else if (e instanceof CallNotPermittedException) {
            status = RefundStatus.UNKNOWN;
            failure = new PaymentFailure(e.getClass().getSimpleName(), e.getMessage() == null ? "환불 원장 Circuit이 열려 있습니다" : e.getMessage());
        } else if (e instanceof RestClientException) {
            status = RefundStatus.UNKNOWN;
            failure = new PaymentFailure(e.getClass().getSimpleName(), e.getMessage() == null ? "환불 원장 HTTP 호출 오류 발생" : e.getMessage());
        } else {
            status = RefundStatus.FAILURE;
            failure = new PaymentFailure(e.getClass().getSimpleName(), e.getMessage() == null ? "환불 실행 도중 알 수 없는 오류 발생" : e.getMessage());
        }

        int retryCount = refundRepository.getRetryCountByRefundId(refundId);
        Instant nextAttemptAt = refundRetryBackoffPolicy.nextAttemptAt(Instant.now(), retryCount);

        if (Objects.requireNonNull(status) == RefundStatus.FAILURE) {
            refundStatusUpdateService.updateStatusToFailure(refundId, failure, nextAttemptAt);
        } else {
            refundStatusUpdateService.updateStatusToUnknown(refundId, failure, nextAttemptAt);
        }

        return new PaymentRefundResult(status, failure);
    }

    private boolean isDatabaseError(Throwable e) {
        return e instanceof SQLException ||
                e instanceof DataAccessException ||
                e instanceof TransactionException ||
                e instanceof PersistenceException;
    }

    private void recordRefundLedger(PGCancelCommand command, RefundExecutionResult result) {
        Refund refund = refundRepository.findById(command.refundId())
                .orElseThrow(() -> new LedgerRefundRecordingException("환불 정보를 찾을 수 없습니다. refundId=" + command.refundId()));
        Payment payment = paymentRepository.findById(refund.getPaymentId())
                .orElseThrow(() -> new LedgerRefundRecordingException("결제 정보를 찾을 수 없습니다. paymentId=" + refund.getPaymentId()));
        LedgerEntryResponse originalEntry = findOriginalPaymentLedgerEntry(refund.getPaymentId());

        if ("REVERSED".equals(originalEntry.status())) {
            log.info(
                    "Payment ledger entry is already reversed. refundId={}, paymentId={}, entryId={}, reversedEntryId={}",
                    refund.getId(),
                    payment.getId(),
                    originalEntry.entryId(),
                    originalEntry.reversedEntryId()
            );
            return;
        }

        LedgerEntryRequest request = new LedgerEntryRequest(
                "REFUND:" + refund.getId() + ":COMPLETED",
                "REFUND",
                refund.getId(),
                refund.getOrderId(),
                payment.getBuyerId(),
                payment.getSellerId(),
                BigDecimal.valueOf(result.refundExtraDetails().refundAmount()),
                "KRW",
                "DEBIT",
                "환불 완료"
        );

        LedgerEntryResponse response = ledgerClient.recordAdjustment(originalEntry.entryId(), request);
        log.info(
                "Refund ledger adjustment recorded. refundId={}, paymentId={}, originalEntryId={}, adjustmentEntryId={}",
                refund.getId(),
                payment.getId(),
                originalEntry.entryId(),
                response == null ? null : response.entryId()
        );
    }

    private LedgerEntryResponse findOriginalPaymentLedgerEntry(String paymentLedgerTransactionId) {
        List<LedgerEntryResponse> entries = ledgerClient.findByTransactionId(paymentLedgerTransactionId);
        if (entries == null || entries.isEmpty()) {
            throw new LedgerRefundRecordingException("원본 결제 원장을 찾을 수 없습니다. transactionId=" + paymentLedgerTransactionId);
        }

        return entries.stream()
                .filter(entry -> "PAYMENT".equals(entry.transactionType()))
                .filter(entry -> "POSTED".equals(entry.status()))
                .findFirst()
                .or(() -> entries.stream()
                        .filter(entry -> "PAYMENT".equals(entry.transactionType()))
                        .filter(entry -> "REVERSED".equals(entry.status()))
                        .findFirst())
                .orElseThrow(() -> new LedgerRefundRecordingException("보정 가능한 원본 결제 원장을 찾을 수 없습니다. transactionId=" + paymentLedgerTransactionId));
    }

    private RefundStatus currentRefundStatus(String refundId) {
        return refundRepository.findById(refundId)
                .map(Refund::getRefundStatus)
                .orElse(RefundStatus.EXECUTING);
    }

    private static class LedgerRefundRecordingException extends RuntimeException {
        private LedgerRefundRecordingException(String message) {
            super(message);
        }
    }
}
