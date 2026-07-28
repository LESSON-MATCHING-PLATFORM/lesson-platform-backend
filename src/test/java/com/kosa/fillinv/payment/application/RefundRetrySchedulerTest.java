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
import static org.mockito.Mockito.doThrow;
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
    @DisplayName("UNKNOWN과 FAILURE 환불 중 재시도 횟수와 시간이 충족된 건만 조회한다")
    void retryFailedRefunds_queriesRetryableRefunds() {
        given(refundRepository.findTop100ByRefundStatusInAndRetryCountLessThanAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                any(),
                any(),
                any()
        ))
                .willReturn(List.of());

        refundRetryScheduler.retryFailedRefunds();

        verify(refundRepository).findTop100ByRefundStatusInAndRetryCountLessThanAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                eq(List.of(RefundStatus.UNKNOWN, RefundStatus.FAILURE)),
                eq(3),
                any(Instant.class)
        );
    }

    @Test
    @DisplayName("조회된 재시도 대상 환불을 RefundProcessor에 재처리 요청한다")
    void retryFailedRefunds_delegatesToRefundProcessor() {
        Refund refund = refund();
        given(refundRepository.findTop100ByRefundStatusInAndRetryCountLessThanAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                any(),
                any(),
                any()
        ))
                .willReturn(List.of(refund));

        refundRetryScheduler.retryFailedRefunds();

        ArgumentCaptor<PGCancelCommand> captor = ArgumentCaptor.forClass(PGCancelCommand.class);
        verify(refundProcessor).processPGCancel(captor.capture());

        PGCancelCommand command = captor.getValue();
        assertThat(command.refundId()).isEqualTo(refund.getId());
        assertThat(command.paymentKey()).isEqualTo(refund.getPaymentKey());
        assertThat(command.orderId()).isEqualTo(refund.getOrderId());
        assertThat(command.reason()).isEqualTo(refund.getRefundReason());
        assertThat(command.amount()).isEqualTo(refund.getRefundAmount());
    }

    @Test
    @DisplayName("한 환불 재처리 실패가 나머지 환불 재처리를 막지 않는다")
    void retryFailedRefunds_whenOneRefundFails_continuesRemainingRefunds() {
        Refund first = refund("refund-001");
        Refund second = refund("refund-002");
        given(refundRepository.findTop100ByRefundStatusInAndRetryCountLessThanAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                any(),
                any(),
                any()
        ))
                .willReturn(List.of(first, second));
        doThrow(new IllegalStateException("retry failed"))
                .when(refundProcessor)
                .processPGCancel(any(PGCancelCommand.class));

        refundRetryScheduler.retryFailedRefunds();

        ArgumentCaptor<PGCancelCommand> captor = ArgumentCaptor.forClass(PGCancelCommand.class);
        verify(refundProcessor, org.mockito.Mockito.times(2)).processPGCancel(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(PGCancelCommand::refundId)
                .containsExactly("refund-001", "refund-002");
    }

    private Refund refund() {
        return refund("refund-001");
    }

    private Refund refund(String refundId) {
        return Refund.builder()
                .id(refundId)
                .paymentId("payment-001")
                .paymentKey("payment-key")
                .orderId("order-001")
                .refundStatus(RefundStatus.UNKNOWN)
                .refundAmount(1000)
                .refundReason("단순 변심")
                .build();
    }
}
