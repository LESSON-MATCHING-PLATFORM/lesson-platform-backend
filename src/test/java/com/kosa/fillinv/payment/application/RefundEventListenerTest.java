package com.kosa.fillinv.payment.application;

import com.kosa.fillinv.payment.entity.Refund;
import com.kosa.fillinv.payment.entity.RefundStatus;
import com.kosa.fillinv.payment.service.RefundProcessor;
import com.kosa.fillinv.payment.service.dto.PGCancelCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundEventListenerTest {

    @Mock
    private RefundProcessor refundProcessor;

    @InjectMocks
    private RefundEventListener refundEventListener;

    @Test
    @DisplayName("환불 이벤트를 PG 취소 명령으로 변환해 RefundProcessor에 위임한다")
    void handleRefundEvent_delegatesToRefundProcessor() {
        Refund refund = Refund.builder()
                .id("refund-001")
                .paymentId("payment-001")
                .paymentKey("payment-key-001")
                .orderId("order-001")
                .refundStatus(RefundStatus.NOT_STARTED)
                .refundAmount(1000)
                .refundReason("단순 변심")
                .build();

        refundEventListener.handleRefundEvent(refund);

        ArgumentCaptor<PGCancelCommand> captor = ArgumentCaptor.forClass(PGCancelCommand.class);
        verify(refundProcessor).processPGCancel(captor.capture());

        PGCancelCommand command = captor.getValue();
        assertThat(command.refundId()).isEqualTo(refund.getId());
        assertThat(command.paymentKey()).isEqualTo(refund.getPaymentKey());
        assertThat(command.orderId()).isEqualTo(refund.getOrderId());
        assertThat(command.reason()).isEqualTo(refund.getRefundReason());
        assertThat(command.amount()).isEqualTo(refund.getRefundAmount());
    }
}
