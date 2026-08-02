package com.kosa.fillinv.payment.service;

import com.kosa.fillinv.payment.entity.Refund;
import com.kosa.fillinv.payment.entity.RefundStatus;
import com.kosa.fillinv.payment.repository.RefundRepository;
import com.kosa.fillinv.booking.entity.BookingStatus;
import com.kosa.fillinv.booking.service.BookingCommandService;
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
    private static final List<BookingStatus> RECOVERABLE_BOOKING_STATUSES = List.of(
            BookingStatus.APPROVAL_PENDING,
            BookingStatus.APPROVED
    );

    private final RefundRepository refundRepository;
    private final BookingCommandService bookingCommandService;

    public void recoverRefundInternalStates() {
        refundRepository.findRefundsPendingInternalStateRecovery(
                        RefundStatus.SUCCESS,
                        RECOVERABLE_BOOKING_STATUSES,
                        PageRequest.of(0, RECOVERY_BATCH_SIZE)
                )
                .forEach(this::recoverSafely);
    }

    private void recoverSafely(Refund refund) {
        try {
            bookingCommandService.cancelByRefund(refund.getOrderId());
        } catch (Exception e) {
            log.error("Refund internal state recovery failed. refundId={}, orderId={}",
                    refund.getId(),
                    refund.getOrderId(),
                    e);
        }
    }
}
