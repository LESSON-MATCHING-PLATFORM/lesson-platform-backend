package com.kosa.fillinv.payment.service;

import com.kosa.fillinv.payment.domain.PSPConfirmationStatus;
import com.kosa.fillinv.payment.domain.PaymentExtraDetails;
import com.kosa.fillinv.payment.domain.PaymentFailure;
import com.kosa.fillinv.payment.domain.PaymentMethod;
import com.kosa.fillinv.payment.domain.PaymentType;
import com.kosa.fillinv.payment.entity.Payment;
import com.kosa.fillinv.payment.entity.PaymentHistory;
import com.kosa.fillinv.payment.entity.PaymentStatus;
import com.kosa.fillinv.payment.repository.PaymentHistoryRepository;
import com.kosa.fillinv.payment.repository.PaymentRepository;
import com.kosa.fillinv.payment.service.dto.PaymentStatusUpdateCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentUpdateServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentHistoryRepository paymentHistoryRepository;

    @InjectMocks
    private PaymentUpdateService paymentUpdateService;

    @Test
    @DisplayName("EXECUTING 변경 시 결제키를 저장하고 이력을 남긴다")
    void updateStatusToExecuting() {
        Payment payment = payment();
        given(paymentRepository.findByOrderId(payment.getOrderId()))
                .willReturn(Optional.of(payment));

        paymentUpdateService.updateStatus(new PaymentStatusUpdateCommand(
                "payment-key-001",
                payment.getOrderId(),
                PaymentStatus.EXECUTING,
                null,
                null
        ));

        PaymentHistory history = savedHistory();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.EXECUTING);
        assertThat(payment.getPaymentKey()).isEqualTo("payment-key-001");
        assertThat(history.getPreviousStatus()).isEqualTo(PaymentStatus.NOT_STARTED);
        assertThat(history.getNewStatus()).isEqualTo(PaymentStatus.EXECUTING);
        assertThat(history.getReason()).isEqualTo("PAYMENT_CONFIRMATION_START");
    }

    @Test
    @DisplayName("SUCCESS 변경 시 승인 상세 정보와 이력을 저장한다")
    void updateStatusToSuccess() {
        Payment payment = executingPayment();
        PaymentExtraDetails details = successDetails();
        given(paymentRepository.findByOrderId(payment.getOrderId()))
                .willReturn(Optional.of(payment));

        paymentUpdateService.updateStatus(new PaymentStatusUpdateCommand(
                "payment-key-001",
                payment.getOrderId(),
                PaymentStatus.SUCCESS,
                details,
                null
        ));

        PaymentHistory history = savedHistory();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getApprovedAt()).isEqualTo(details.approvedAt());
        assertThat(payment.getPaymentMethod()).isEqualTo(details.method());
        assertThat(payment.getPspRaw()).isEqualTo(details.pspRawData());
        assertThat(history.getPreviousStatus()).isEqualTo(PaymentStatus.EXECUTING);
        assertThat(history.getNewStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(history.getReason()).isEqualTo("PAYMENT_CONFIRM_DONE");
    }

    @Test
    @DisplayName("FAILURE 변경 시 실패 이력을 저장한다")
    void updateStatusToFailure() {
        Payment payment = executingPayment();
        PaymentFailure failure = new PaymentFailure("REJECT_CARD_PAYMENT", "잔액 부족");
        given(paymentRepository.findByOrderId(payment.getOrderId()))
                .willReturn(Optional.of(payment));

        paymentUpdateService.updateStatus(new PaymentStatusUpdateCommand(
                "payment-key-001",
                payment.getOrderId(),
                PaymentStatus.FAILURE,
                null,
                failure
        ));

        PaymentHistory history = savedHistory();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILURE);
        assertThat(history.getPreviousStatus()).isEqualTo(PaymentStatus.EXECUTING);
        assertThat(history.getNewStatus()).isEqualTo(PaymentStatus.FAILURE);
        assertThat(history.getReason()).contains("REJECT_CARD_PAYMENT");
    }

    @Test
    @DisplayName("UNKNOWN 변경 시 불명확 이력을 저장한다")
    void updateStatusToUnknown() {
        Payment payment = executingPayment();
        PaymentFailure failure = new PaymentFailure("ResourceAccessException", "timeout");
        given(paymentRepository.findByOrderId(payment.getOrderId()))
                .willReturn(Optional.of(payment));

        paymentUpdateService.updateStatus(new PaymentStatusUpdateCommand(
                "payment-key-001",
                payment.getOrderId(),
                PaymentStatus.UNKNOWN,
                null,
                failure
        ));

        PaymentHistory history = savedHistory();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(history.getPreviousStatus()).isEqualTo(PaymentStatus.EXECUTING);
        assertThat(history.getNewStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(history.getReason()).contains("ResourceAccessException");
    }

    @Test
    @DisplayName("FAILURE 결제는 EXECUTING으로 재시도할 수 있고 이력을 남긴다")
    void updateStatus_failureToExecuting() {
        Payment payment = failurePayment();
        given(paymentRepository.findByOrderId(payment.getOrderId()))
                .willReturn(Optional.of(payment));

        paymentUpdateService.updateStatus(new PaymentStatusUpdateCommand(
                "payment-key-retry",
                payment.getOrderId(),
                PaymentStatus.EXECUTING,
                null,
                null
        ));

        PaymentHistory history = savedHistory();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.EXECUTING);
        assertThat(payment.getPaymentKey()).isEqualTo("payment-key-retry");
        assertThat(history.getPreviousStatus()).isEqualTo(PaymentStatus.FAILURE);
        assertThat(history.getNewStatus()).isEqualTo(PaymentStatus.EXECUTING);
    }

    @Test
    @DisplayName("금지 전이 시 이력을 저장하지 않아 실제 상태와 이력이 불일치하지 않는다")
    void updateStatus_invalidTransitionDoesNotSaveHistory() {
        Payment payment = successPayment();
        given(paymentRepository.findByOrderId(payment.getOrderId()))
                .willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentUpdateService.updateStatus(new PaymentStatusUpdateCommand(
                "payment-key-001",
                payment.getOrderId(),
                PaymentStatus.FAILURE,
                null,
                new PaymentFailure("INVALID", "invalid")
        ))).isInstanceOf(IllegalStateException.class);

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(paymentHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("SUCCESS에서 EXECUTING 금지 전이 시 이력을 저장하지 않는다")
    void updateStatus_successToExecutingDoesNotSaveHistory() {
        Payment payment = successPayment();
        given(paymentRepository.findByOrderId(payment.getOrderId()))
                .willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentUpdateService.updateStatus(new PaymentStatusUpdateCommand(
                "payment-key-retry",
                payment.getOrderId(),
                PaymentStatus.EXECUTING,
                null,
                null
        ))).isInstanceOf(IllegalStateException.class);

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(paymentHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("FAILURE에서 UNKNOWN 금지 전이 시 이력을 저장하지 않는다")
    void updateStatus_failureToUnknownDoesNotSaveHistory() {
        Payment payment = failurePayment();
        given(paymentRepository.findByOrderId(payment.getOrderId()))
                .willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentUpdateService.updateStatus(new PaymentStatusUpdateCommand(
                "payment-key-001",
                payment.getOrderId(),
                PaymentStatus.UNKNOWN,
                null,
                new PaymentFailure("ResourceAccessException", "timeout")
        ))).isInstanceOf(IllegalStateException.class);

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILURE);
        verify(paymentHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("없는 orderId로 상태 변경 시 예외가 발생한다")
    void updateStatus_whenPaymentNotFound() {
        given(paymentRepository.findByOrderId("missing-order"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentUpdateService.updateStatus(new PaymentStatusUpdateCommand(
                "payment-key-001",
                "missing-order",
                PaymentStatus.EXECUTING,
                null,
                null
        ))).isInstanceOf(RuntimeException.class);
    }

    private PaymentHistory savedHistory() {
        ArgumentCaptor<PaymentHistory> captor = ArgumentCaptor.forClass(PaymentHistory.class);
        verify(paymentHistoryRepository).save(captor.capture());
        return captor.getValue();
    }

    private Payment executingPayment() {
        Payment payment = payment();
        payment.markExecuting();
        return payment;
    }

    private Payment failurePayment() {
        Payment payment = executingPayment();
        payment.markFail();
        return payment;
    }

    private Payment successPayment() {
        Payment payment = executingPayment();
        payment.markSuccess();
        return payment;
    }

    private Payment payment() {
        return Payment.builder()
                .id("payment-001")
                .buyerId("mentee-001")
                .sellerId("mentor-001")
                .orderId("schedule-001")
                .orderName("자바 멘토링 - 30분")
                .amount(30000)
                .build();
    }

    private PaymentExtraDetails successDetails() {
        return new PaymentExtraDetails(
                PaymentType.NORMAL,
                PaymentMethod.EASY_PAY,
                Instant.parse("2026-07-24T05:00:00Z"),
                "자바 멘토링 - 30분",
                PSPConfirmationStatus.DONE,
                30000L,
                "raw"
        );
    }
}
