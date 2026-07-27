package com.kosa.fillinv.payment.application;

import com.kosa.fillinv.payment.entity.Refund;
import com.kosa.fillinv.payment.entity.RefundStatus;
import com.kosa.fillinv.payment.repository.RefundRepository;
import com.kosa.fillinv.payment.service.RefundProcessor;
import com.kosa.fillinv.payment.service.dto.PGCancelCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundRetrySchedulerTest {

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private RefundProcessor refundProcessor;

    @InjectMocks
    private RefundRetryScheduler refundRetryScheduler;

    @Test
    @DisplayName("UNKNOWN 환불 중 재시도 횟수와 시간이 충족된 건만 조회한다")
    void retryUnknownRefunds_queriesRetryableUnknownRefunds() {
        given(refundRepository.findTop100ByRefundStatusAndRetryCountLessThanAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                any(),
                any(),
                any()
        ))
                .willReturn(List.of());

        refundRetryScheduler.retryUnknownRefunds();

        verify(refundRepository).findTop100ByRefundStatusAndRetryCountLessThanAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                eq(RefundStatus.UNKNOWN),
                eq(3),
                any(Instant.class)
        );
    }

    @Test
    @DisplayName("조회된 UNKNOWN 환불을 RefundProcessor에 재처리 요청한다")
    void retryUnknownRefunds_delegatesToRefundProcessor() {
        Refund refund = refund();
        given(refundRepository.findTop100ByRefundStatusAndRetryCountLessThanAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                any(),
                any(),
                any()
        ))
                .willReturn(List.of(refund));

        refundRetryScheduler.retryUnknownRefunds();

        ArgumentCaptor<PGCancelCommand> captor = ArgumentCaptor.forClass(PGCancelCommand.class);
        verify(refundProcessor).processPGCancel(captor.capture());

        PGCancelCommand command = captor.getValue();
        assertThat(command.refundId()).isEqualTo(refund.getId());
        assertThat(command.paymentKey()).isEqualTo(refund.getPaymentKey());
        assertThat(command.orderId()).isEqualTo(refund.getOrderId());
        assertThat(command.reason()).isEqualTo(refund.getRefundReason());
        assertThat(command.amount()).isEqualTo(refund.getRefundAmount());
    }

    private Refund refund() {
        return Refund.builder()
                .id("refund-001")
                .paymentId("payment-001")
                .paymentKey("payment-key")
                .orderId("order-001")
                .refundStatus(RefundStatus.UNKNOWN)
                .refundAmount(1000)
                .refundReason("단순 변심")
                .build();
    }
}
