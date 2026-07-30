package com.kosa.fillinv.payment.application;

import com.kosa.fillinv.payment.service.RefundInternalStateRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefundInternalStateRecoveryScheduler {

    private final RefundInternalStateRecoveryService refundInternalStateRecoveryService;

    @Scheduled(fixedDelayString = "${payment.refund.internal-recovery-fixed-delay-ms:30000}")
    public void recoverRefundInternalStates() {
        refundInternalStateRecoveryService.recoverRefundInternalStates();
    }
}
