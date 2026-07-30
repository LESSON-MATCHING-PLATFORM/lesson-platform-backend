package com.kosa.fillinv.payment.application;

import com.kosa.fillinv.payment.service.RefundInternalStateRecoveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundInternalStateRecoverySchedulerTest {

    @Mock
    private RefundInternalStateRecoveryService refundInternalStateRecoveryService;

    @InjectMocks
    private RefundInternalStateRecoveryScheduler refundInternalStateRecoveryScheduler;

    @Test
    @DisplayName("환불 내부 상태 복구 스케줄러는 복구 서비스를 호출한다")
    void recoverRefundInternalStates_delegatesToRecoveryService() {
        refundInternalStateRecoveryScheduler.recoverRefundInternalStates();

        verify(refundInternalStateRecoveryService).recoverRefundInternalStates();
    }
}
