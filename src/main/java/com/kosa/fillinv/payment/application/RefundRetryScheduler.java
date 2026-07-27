package com.kosa.fillinv.payment.application;

import com.kosa.fillinv.payment.entity.Refund;
import com.kosa.fillinv.payment.entity.RefundStatus;
import com.kosa.fillinv.payment.repository.RefundRepository;
import com.kosa.fillinv.payment.service.RefundProcessor;
import com.kosa.fillinv.payment.service.dto.PGCancelCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class RefundRetryScheduler {

    private static final int MAX_RETRY_COUNT = 3;

    private final RefundRepository refundRepository;
    private final RefundProcessor refundProcessor;

    @Scheduled(fixedDelayString = "${payment.refund.retry-fixed-delay-ms:10000}")
    public void retryUnknownRefunds() {
        refundRepository.findTop100ByRefundStatusAndRetryCountLessThanAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                        RefundStatus.UNKNOWN,
                        MAX_RETRY_COUNT,
                        Instant.now()
                )
                .forEach(this::processRefund);
    }

    private void processRefund(Refund refund) {
        refundProcessor.processPGCancel(new PGCancelCommand(
                refund.getId(),
                refund.getPaymentKey(),
                refund.getOrderId(),
                refund.getRefundReason(),
                refund.getRefundAmount()
        ));
    }
}
