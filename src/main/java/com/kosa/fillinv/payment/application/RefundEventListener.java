package com.kosa.fillinv.payment.application;

import com.kosa.fillinv.payment.entity.Refund;
import com.kosa.fillinv.payment.service.RefundProcessor;
import com.kosa.fillinv.payment.service.dto.PGCancelCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RefundEventListener {

    private final RefundProcessor refundProcessor;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRefundEvent(Refund refund) {
        refundProcessor.processPGCancel(new PGCancelCommand(
                refund.getId(),
                refund.getPaymentKey(),
                refund.getOrderId(),
                refund.getRefundReason(),
                refund.getRefundAmount()
        ));
    }
}
