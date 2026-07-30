package com.kosa.fillinv.payment.service;

import com.kosa.fillinv.payment.entity.Refund;
import com.kosa.fillinv.payment.entity.RefundStatus;
import com.kosa.fillinv.payment.repository.RefundRepository;
import com.kosa.fillinv.schedule.entity.ScheduleStatus;
import com.kosa.fillinv.schedule.service.ScheduleCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundInternalStateRecoveryService {

    private static final int RECOVERY_BATCH_SIZE = 100;
    private static final List<ScheduleStatus> RECOVERABLE_SCHEDULE_STATUSES = List.of(
            ScheduleStatus.APPROVAL_PENDING,
            ScheduleStatus.APPROVED
    );

    private final RefundRepository refundRepository;
    private final ScheduleCommandService scheduleCommandService;

    public void recoverRefundInternalStates() {
        refundRepository.findRefundsPendingInternalStateRecovery(
                        RefundStatus.SUCCESS,
                        RECOVERABLE_SCHEDULE_STATUSES,
                        PageRequest.of(0, RECOVERY_BATCH_SIZE)
                )
                .forEach(this::recoverSafely);
    }

    private void recoverSafely(Refund refund) {
        try {
            scheduleCommandService.cancelByRefund(refund.getOrderId());
        } catch (Exception e) {
            log.error("Refund internal state recovery failed. refundId={}, orderId={}",
                    refund.getId(),
                    refund.getOrderId(),
                    e);
        }
    }
}
