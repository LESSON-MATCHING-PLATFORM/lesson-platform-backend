package com.kosa.fillinv.payment.service.dto;

import com.kosa.fillinv.payment.application.RefundEventListener;
import com.kosa.fillinv.payment.entity.Payment;
import com.kosa.fillinv.payment.repository.PaymentRepository;
import com.kosa.fillinv.payment.repository.RefundRepository;
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
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("local")
class RefundServiceTest {

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

}
