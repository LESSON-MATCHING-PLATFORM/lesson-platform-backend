package com.kosa.fillinv.payment.service;

import com.kosa.fillinv.payment.client.TossPaymentClient;
import com.kosa.fillinv.payment.client.dto.PaymentCancelCommand;
import com.kosa.fillinv.payment.domain.PSPConfirmationException;
import com.kosa.fillinv.payment.domain.PaymentFailure;
import com.kosa.fillinv.payment.domain.RefundExecutionResult;
import com.kosa.fillinv.payment.entity.RefundStatus;
import com.kosa.fillinv.payment.outbox.PaymentOutboxService;
import com.kosa.fillinv.payment.repository.RefundRepository;
import com.kosa.fillinv.payment.service.dto.PGCancelCommand;
import com.kosa.fillinv.payment.service.dto.PaymentRefundResult;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RefundProcessor {

    private final RefundStatusUpdateService refundStatusUpdateService;
    private final TossPaymentClient tossPaymentClient;
    private final RefundRepository refundRepository;
    private final RefundRetryBackoffPolicy refundRetryBackoffPolicy;
    private final PaymentOutboxService paymentOutboxService;
    private final TransactionTemplate transactionTemplate;

    public PaymentRefundResult processPGCancel(PGCancelCommand command) {
        refundStatusUpdateService.updateStatusToExecuting(command.refundId(), Instant.now());

        try {
            RefundExecutionResult result = tossPaymentClient.cancel(
                    new PaymentCancelCommand(
                            command.refundId(),
                            command.paymentKey(),
                            command.orderId(),
                            command.reason(),
                            command.amount()
                    ));

            transactionTemplate.executeWithoutResult(status -> {
                refundStatusUpdateService.updateStatusToSuccess(
                        command.refundId(),
                        result.refundExtraDetails().transactionKey(),
                        result.refundExtraDetails().refundedAt(),
                        result.refundExtraDetails().pspRawData()
                );
                paymentOutboxService.saveRefundCompletedEvent(command, result);
            });

            return new PaymentRefundResult(RefundStatus.SUCCESS, null);
        } catch (Exception e) {
            return handlePGCancelError(command.refundId(), e);
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
        } else if (e instanceof ResourceAccessException) {
            status = RefundStatus.UNKNOWN;
            failure = new PaymentFailure(e.getClass().getSimpleName(), e.getMessage() == null ? "환불 실행 도중 외부 연결 오류 발생" : e.getMessage());
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
}
