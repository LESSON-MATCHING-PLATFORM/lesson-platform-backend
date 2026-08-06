package com.kosa.fillinv.payment.service;

import com.kosa.fillinv.global.exception.ResourceException;
import com.kosa.fillinv.payment.domain.PaymentFailure;
import com.kosa.fillinv.payment.entity.*;
import com.kosa.fillinv.payment.repository.RefundHistoryRepository;
import com.kosa.fillinv.payment.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefundStatusUpdateService {

    private static final List<RefundStatus> EXECUTING_CLAIMABLE_STATUSES = List.of(
            RefundStatus.NOT_STARTED,
            RefundStatus.UNKNOWN,
            RefundStatus.FAILURE
    );

    private final RefundRepository refundRepository;
    private final RefundHistoryRepository refundHistoryRepository;

    private RefundHistory createRefundHistory(Refund refund, RefundStatus newStatus, String reason) {
        return RefundHistory.builder()
                .id(UUID.randomUUID().toString())
                .paymentKey(refund.getPaymentKey())
                .previousStatus(refund.getRefundStatus())
                .newStatus(newStatus)
                .reason(reason)
                .build();
    }

    @Transactional
    public boolean tryUpdateStatusToExecuting(String refundId, Instant executedAt) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new ResourceException.NotFound("환불 정보 없음"));
        RefundStatus previousStatus = refund.getRefundStatus();

        if (!EXECUTING_CLAIMABLE_STATUSES.contains(previousStatus)) {
            return false;
        }

        int updated = refundRepository.markExecutingIfStatusIn(
                refundId,
                EXECUTING_CLAIMABLE_STATUSES,
                executedAt
        );

        if (updated == 0) {
            return false;
        }

        refundHistoryRepository.save(
                RefundHistory.builder()
                        .id(UUID.randomUUID().toString())
                        .paymentKey(refund.getPaymentKey())
                        .previousStatus(previousStatus)
                        .newStatus(RefundStatus.EXECUTING)
                        .reason("PAYMENT_CANCELATION_START")
                        .build()
        );

        return true;
    }

    @Transactional
    public void updateStatusToExecuting(String refundId, Instant executedAt) {
        if (!tryUpdateStatusToExecuting(refundId, executedAt)) {
            throw new IllegalStateException("EXECUTING 상태로 변경할 수 없습니다.");
        }
    }

    @Transactional
    public void updateStatusToSuccess(String refundId, String transactionKey, Instant refundedAt, String pspRawData) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new ResourceException.NotFound("환불 정보 없음"));

        refundHistoryRepository.save(
                createRefundHistory(refund, RefundStatus.SUCCESS, "PAYMENT_CANCELATION_DONE")
        );

        refund.markSuccess(transactionKey, refundedAt, pspRawData);
    }

    @Transactional
    public void updateStatusToFailure(String refundId, PaymentFailure failure, Instant nextAttemptTime) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new ResourceException.NotFound("환불 정보 없음"));

        refundHistoryRepository.save(
                createRefundHistory(refund, RefundStatus.FAILURE, failure == null ? null : failure.toString())
        );

        refund.markFail(nextAttemptTime);
    }

    @Transactional
    public void updateStatusToUnknown(String refundId, PaymentFailure failure, Instant nextAttemptTime) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new ResourceException.NotFound("환불 정보 없음"));

        RefundHistory refundHistory = createRefundHistory(refund, RefundStatus.UNKNOWN, failure == null ? null : failure.toString());
        refundHistoryRepository.save(refundHistory);

        refund.markUnknown(nextAttemptTime);
    }
}
