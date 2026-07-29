package com.kosa.fillinv.payment.service.dto;

import com.kosa.fillinv.global.exception.ResourceException;
import com.kosa.fillinv.payment.entity.Payment;
import com.kosa.fillinv.payment.entity.Refund;
import com.kosa.fillinv.payment.entity.RefundStatus;
import com.kosa.fillinv.payment.repository.PaymentRepository;
import com.kosa.fillinv.payment.repository.RefundRepository;
import com.kosa.fillinv.payment.service.RefundService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("local")
class RefundServiceTest {

    @MockitoBean
    private PaymentRepository paymentRepository;

    @MockitoBean
    private RefundRepository refundRepository;

    @Autowired
    private RefundService refundService;

    private static PaymentRefundCommand mockCommand() {
        String paymentId = "paymentId";
        String cancelReason = "단순 변심";
        int refundAmount = 1000;

        return new PaymentRefundCommand(paymentId, cancelReason, refundAmount);
    }

    private static Payment mockPayment(PaymentRefundCommand command) {
        Payment payment = Payment.builder()
                .id(command.paymentId())
                .buyerId("buyerId")
                .sellerId("sellerId")
                .orderId("orderId")
                .orderName("orderName")
                .amount(1000)
                .build();
        payment.markExecuting();
        payment.markSuccess();
        payment.setPaymentKey("payment-key");
        return payment;
    }

    @Test
    @DisplayName("환불 요청 시 환불 객체를 생성한다")
    void refund_whenPaymentCancelRequested() {
        // given
        PaymentRefundCommand command = mockCommand();

        when(paymentRepository.findById(command.paymentId()))
                .thenReturn(Optional.of(mockPayment(command)));
        when(refundRepository.existsByPaymentId(command.paymentId()))
                .thenReturn(false);
        when(refundRepository.save(any(Refund.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        RefundDTO refund = refundService.refund(command);

        // then
        assertThat(refund.paymentId()).isEqualTo(command.paymentId());
        assertThat(refund.refundReason()).isEqualTo(command.cancelReason());
        assertThat(refund.refundAmount()).isEqualTo(command.refundAmount());

        var captor = forClass(Refund.class);
        verify(refundRepository).save(captor.capture());

        Refund saved = captor.getValue();
        assertThat(saved.getPaymentId()).isEqualTo(command.paymentId());
        assertThat(saved.getPaymentKey()).isEqualTo("payment-key");
        assertThat(saved.getOrderId()).isEqualTo("orderId");
        assertThat(saved.getRefundStatus()).isEqualTo(RefundStatus.NOT_STARTED);
        assertThat(saved.getRefundAmount()).isEqualTo(command.refundAmount());
        assertThat(saved.getRefundReason()).isEqualTo(command.cancelReason());
    }

    @Test
    @DisplayName("존재하지 않는 결제는 환불 요청을 생성할 수 없다")
    void refund_whenPaymentNotFound() {
        PaymentRefundCommand command = mockCommand();
        when(paymentRepository.findById(command.paymentId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> refundService.refund(command))
                .isInstanceOf(ResourceException.NotFound.class)
                .hasMessageContaining("결제 정보가 존재하지 않습니다.");

        verifyRefundNotCreated();
    }

    @Test
    @DisplayName("성공하지 않은 결제는 환불 요청을 생성할 수 없다")
    void refund_whenPaymentIsNotSuccess() {
        PaymentRefundCommand command = mockCommand();
        Payment payment = Payment.builder()
                .id(command.paymentId())
                .buyerId("buyerId")
                .sellerId("sellerId")
                .orderId("orderId")
                .orderName("orderName")
                .amount(1000)
                .build();
        when(paymentRepository.findById(command.paymentId()))
                .thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> refundService.refund(command))
                .isInstanceOf(ResourceException.InvalidArgument.class)
                .hasMessageContaining("성공한 결제만 환불할 수 있습니다.");

        verifyRefundNotCreated();
    }

    @Test
    @DisplayName("환불 사유가 비어 있으면 환불 요청을 생성할 수 없다")
    void refund_whenCancelReasonIsBlank() {
        PaymentRefundCommand command = new PaymentRefundCommand("paymentId", " ", 1000);
        givenPayment(command);

        assertThatThrownBy(() -> refundService.refund(command))
                .isInstanceOf(ResourceException.InvalidArgument.class)
                .hasMessageContaining("환불 사유는 필수입니다.");

        verifyRefundNotCreated();
    }

    @Test
    @DisplayName("환불 금액이 0 이하이면 환불 요청을 생성할 수 없다")
    void refund_whenRefundAmountIsNotPositive() {
        PaymentRefundCommand command = new PaymentRefundCommand("paymentId", "단순 변심", 0);
        givenPayment(command);

        assertThatThrownBy(() -> refundService.refund(command))
                .isInstanceOf(ResourceException.InvalidArgument.class)
                .hasMessageContaining("환불 금액은 0보다 커야 합니다.");

        verifyRefundNotCreated();
    }

    @Test
    @DisplayName("환불 금액이 결제 금액보다 크면 환불 요청을 생성할 수 없다")
    void refund_whenRefundAmountExceedsPaymentAmount() {
        PaymentRefundCommand command = new PaymentRefundCommand("paymentId", "단순 변심", 1001);
        givenPayment(command);

        assertThatThrownBy(() -> refundService.refund(command))
                .isInstanceOf(ResourceException.InvalidArgument.class)
                .hasMessageContaining("환불 금액은 결제 금액보다 클 수 없습니다.");

        verifyRefundNotCreated();
    }

    @Test
    @DisplayName("이미 환불 요청이 존재하면 중복 환불 요청을 생성할 수 없다")
    void refund_whenRefundAlreadyExists() {
        PaymentRefundCommand command = mockCommand();
        givenPayment(command);
        when(refundRepository.existsByPaymentId(command.paymentId()))
                .thenReturn(true);

        assertThatThrownBy(() -> refundService.refund(command))
                .isInstanceOf(ResourceException.InvalidArgument.class)
                .hasMessageContaining("이미 환불 요청이 존재합니다.");

        verifyRefundNotCreated();
    }

    private void givenPayment(PaymentRefundCommand command) {
        when(paymentRepository.findById(command.paymentId()))
                .thenReturn(Optional.of(mockPayment(command)));
    }

    private void verifyRefundNotCreated() {
        verify(refundRepository, never()).save(any());
    }

}
