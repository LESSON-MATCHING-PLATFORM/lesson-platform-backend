package com.kosa.fillinv.payment.service.dto;

import com.kosa.fillinv.payment.application.RefundEventListener;
import com.kosa.fillinv.payment.client.TossPaymentClient;
import com.kosa.fillinv.payment.domain.PSPConfirmationException;
import com.kosa.fillinv.payment.domain.RefundExecutionResult;
import com.kosa.fillinv.payment.domain.RefundExtraDetails;
import com.kosa.fillinv.payment.entity.Payment;
import com.kosa.fillinv.payment.repository.PaymentRepository;
import com.kosa.fillinv.payment.repository.RefundRepository;
import com.kosa.fillinv.payment.service.RefundStatusUpdateService;
import com.kosa.fillinv.payment.service.RefundService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("local")
class RefundServiceTest {

    @MockitoBean
    private RefundStatusUpdateService refundStatusUpdateService;

    @MockitoBean
    private TossPaymentClient tossPaymentClient;

    @MockitoBean
    private PaymentRepository paymentRepository;

    @MockitoBean
    private ApplicationContext applicationContext;

    @MockitoSpyBean
    private RefundRepository refundRepository;

    @MockitoBean
    private RefundEventListener refundEventListener;

    @Autowired
    private RefundService refundService;

    private static PaymentRefundCommand mockCommand() {
        String paymentId = "paymentId";
        String cancelReason = "orderId";
        int refundAmount = 1000;

        return new PaymentRefundCommand(paymentId, cancelReason, refundAmount);
    }

    private static Payment mockPayment(PaymentRefundCommand command) {
        return Payment.builder()
                .id(command.paymentId())
                .buyerId("buyerId")
                .orderId("orderId")
                .orderName("orderName")
                .amount(1000)
                .build();
    }

    @Test
    @DisplayName("환불 요청 시 환불 객체를 생성한다")
    void refund_whenPaymentCancelRequested() {
        // given
        PaymentRefundCommand command = mockCommand();

        when(paymentRepository.findById(command.paymentId()))
                .thenReturn(Optional.of(mockPayment(command)));

        // when
        RefundDTO refund = refundService.refund(command);

        // then
        assertThat(refund.paymentId()).isEqualTo(command.paymentId());
        assertThat(refund.refundReason()).isEqualTo(command.cancelReason());
        assertThat(refund.refundAmount()).isEqualTo(command.refundAmount());
    }


    @Test
    @DisplayName("환불 요청에 해당하는 pg cancel을 실행하고 정상적으로 처리된다.")
    void processPGCancel_whenPaymentCancelRequested_and_pgCancelSuccess() throws InterruptedException {
        // given
        PaymentRefundCommand command = mockCommand();
        when(paymentRepository.findById(command.paymentId()))
                .thenReturn(Optional.of(mockPayment(command)));
        RefundDTO refund = refundService.refund(command);

        when(refundRepository.getRetryCountByRefundId(refund.refundId()))
                .thenReturn(0);
        when(tossPaymentClient.cancel(any()))
                .thenReturn(mockSuccessResult());
        // when
        refundService.processPGCancel(new PGCancelCommand(
                refund.refundId(),
                refund.paymentKey(),
                refund.orderId(),
                refund.refundReason(),
                refund.refundAmount()
        ));

        // then
        verify(refundStatusUpdateService).updateStatusToExecuting(eq(refund.refundId()), any());
        verify(tossPaymentClient).cancel(any());
        verify(refundStatusUpdateService).updateStatusToSuccess(
                eq(refund.refundId()),
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("환불 요청에 해당하는 pg cancel을 실행하고 실패한다.")
    void processPGCancel_whenPaymentCancelRequested_and_pgCancelFailed() {
        // given
        PaymentRefundCommand command = mockCommand();
        when(paymentRepository.findById(command.paymentId()))
                .thenReturn(Optional.of(mockPayment(command)));
        RefundDTO refund = refundService.refund(command);

        when(refundRepository.getRetryCountByRefundId(refund.refundId()))
                .thenReturn(0);
        when(tossPaymentClient.cancel(any()))
                .thenThrow(mockPSPConfirmationExceptionFail());
        // when
        refundService.processPGCancel(new PGCancelCommand(
                refund.refundId(),
                refund.paymentKey(),
                refund.orderId(),
                refund.refundReason(),
                refund.refundAmount()
        ));

        // then
        verify(refundStatusUpdateService).updateStatusToExecuting(eq(refund.refundId()), any());
        verify(tossPaymentClient).cancel(any());
        verify(refundStatusUpdateService).updateStatusToFailure(
                eq(refund.refundId()),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("환불 요청에 해당하는 pg cancel을 실행하고 결과를 알 수 없음 처리한다.")
    void processPGCancel_whenPaymentCancelRequested_and_pgCancelUnknown() {
        // given
        PaymentRefundCommand command = mockCommand();
        when(paymentRepository.findById(command.paymentId()))
                .thenReturn(Optional.of(mockPayment(command)));
        RefundDTO refund = refundService.refund(command);

        when(refundRepository.getRetryCountByRefundId(refund.refundId()))
                .thenReturn(0);
        when(tossPaymentClient.cancel(any()))
                .thenThrow(mockPSPConfirmationExceptionUnknown());
        // when
        refundService.processPGCancel(new PGCancelCommand(
                refund.refundId(),
                refund.paymentKey(),
                refund.orderId(),
                refund.refundReason(),
                refund.refundAmount()
        ));

        // then
        verify(refundStatusUpdateService).updateStatusToExecuting(eq(refund.refundId()), any());
        verify(tossPaymentClient).cancel(any());
        verify(refundStatusUpdateService).updateStatusToUnknown(
                eq(refund.refundId()),
                any(),
                any()
        );
    }

    private PSPConfirmationException mockPSPConfirmationExceptionUnknown() {
        return PSPConfirmationException.builder()
                .errorCode("400")
                .errorMessage("알 수 없음")
                .isSuccess(false)
                .isFailure(false)
                .isUnknown(true)
                .isRetryable(true)
                .build();
    }

    private PSPConfirmationException mockPSPConfirmationExceptionFail() {
        return PSPConfirmationException.builder()
                .errorCode("400")
                .errorMessage("실패")
                .isSuccess(false)
                .isFailure(true)
                .isUnknown(false)
                .isRetryable(false)
                .build();
    }

    private RefundExecutionResult mockSuccessResult() {
        return new RefundExecutionResult(
                "paymentKey",
                "orderId",
                mock(RefundExtraDetails.class)
        );
    }

}