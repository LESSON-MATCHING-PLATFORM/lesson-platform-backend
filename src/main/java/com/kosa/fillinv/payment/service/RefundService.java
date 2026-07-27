package com.kosa.fillinv.payment.service;

import com.kosa.fillinv.global.exception.ResourceException;
import com.kosa.fillinv.payment.client.TossPaymentClient;
import com.kosa.fillinv.payment.client.dto.PaymentCancelCommand;
import com.kosa.fillinv.payment.domain.PSPConfirmationException;
import com.kosa.fillinv.payment.domain.PaymentFailure;
import com.kosa.fillinv.payment.domain.RefundExecutionResult;
import com.kosa.fillinv.payment.entity.Payment;
import com.kosa.fillinv.payment.entity.Refund;
import com.kosa.fillinv.payment.entity.RefundStatus;
import com.kosa.fillinv.payment.repository.PaymentRepository;
import com.kosa.fillinv.payment.repository.RefundRepository;
import com.kosa.fillinv.payment.service.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class RefundService {

    private static final long BASE_DELAY_SECONDS = 10L;
    private static final long MAX_DELAY_SECONDS = 300L;
    private final RefundStatusUpdateService refundStatusUpdateService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final TossPaymentClient tossPaymentClient;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;

    @Transactional
    public RefundDTO refund(PaymentRefundCommand command) {
        Refund refund = refundRepository.save(createRefund(command));

        applicationEventPublisher.publishEvent(refund);

        return RefundDTO.of(refund);
    }

    public PaymentRefundResult processPGCancel(PGCancelCommand command) {
        try {
            refundStatusUpdateService.updateStatusToExecuting(command.refundId(), Instant.now());

            RefundExecutionResult result = tossPaymentClient.cancel(
                    new PaymentCancelCommand(command.paymentKey(), command.orderId(), command.reason(), command.amount()));

            refundStatusUpdateService.updateStatusToSuccess(
                    command.refundId(),
                    result.refundExtraDetails().transactionKey(),
                    result.refundExtraDetails().refundedAt(),
                    result.refundExtraDetails().pspRawData()
            );

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
        } else if (e instanceof SQLException) {
            status = RefundStatus.UNKNOWN;
            failure = new PaymentFailure(e.getClass().getSimpleName(), e.getMessage() == null ? "환불 실행 도중 데이터베이스 관련 오류 발생" : e.getMessage());
        } else if (e instanceof ResourceAccessException) { // time out or network
            status = RefundStatus.UNKNOWN;
            failure = new PaymentFailure(e.getClass().getSimpleName(), e.getMessage() == null ? "환불 실행 도중 외부 연결 오류 발생" : e.getMessage());
        } else {
            status = RefundStatus.FAILURE;
            failure = new PaymentFailure(e.getClass().getSimpleName(), e.getMessage() == null ? "환불 실행 도중 알 수 없는 오류 발생" : e.getMessage());
        }

        int retryCount = refundRepository.getRetryCountByRefundId(refundId);

        if (retryCount >= 3) {
            // Todo: MAX_RETRY_COUNT보다 클 경우 처리
        }

        if (Objects.requireNonNull(status) == RefundStatus.FAILURE) {
            refundStatusUpdateService.updateStatusToFailure(
                    refundId, failure, calculateNextAttemptTime(Instant.now(), retryCount));
        } else {
            refundStatusUpdateService.updateStatusToUnknown(
                    refundId, failure, calculateNextAttemptTime(Instant.now(), retryCount));
        }

        return new PaymentRefundResult(status, failure);
    }

    private Refund createRefund(PaymentRefundCommand command) {
        Payment payment = paymentRepository.findById(command.paymentId())
                .orElseThrow(() -> new ResourceException.NotFound("결제 정보가 존재하지 않습니다."));

        Refund newRefund = Refund.builder()
                .id(UUID.randomUUID().toString())
                .paymentId(command.paymentId())
                .paymentKey(payment.getPaymentKey())
                .orderId(payment.getOrderId())
                .refundAmount(command.refundAmount())
                .refundReason(command.cancelReason())
                .refundStatus(RefundStatus.NOT_STARTED)
                .build();
        return newRefund;
    }

    private Instant calculateNextAttemptTime(Instant now, int retryCount) {
        long exponentialDelay = BASE_DELAY_SECONDS * (1L << retryCount);

        long cappedDelay = Math.min(exponentialDelay, MAX_DELAY_SECONDS);

        long jitter = ThreadLocalRandom.current().nextLong(cappedDelay);

        return now.plusSeconds(jitter);
    }
}
