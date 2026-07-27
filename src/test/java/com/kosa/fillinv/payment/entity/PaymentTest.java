package com.kosa.fillinv.payment.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentTest {

    @Test
    @DisplayName("결제 생성 시 상태는 NOT_STARTED다")
    void payment_startsWithNotStarted() {
        Payment payment = payment();

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.NOT_STARTED);
    }

    @Test
    @DisplayName("결제 상태는 NOT_STARTED에서 EXECUTING으로 변경된다")
    void markExecuting_fromNotStarted() {
        Payment payment = payment();

        payment.markExecuting();

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.EXECUTING);
    }

    @Test
    @DisplayName("결제 상태는 EXECUTING에서 SUCCESS로 변경된다")
    void markSuccess_fromExecuting() {
        Payment payment = payment();
        payment.markExecuting();

        payment.markSuccess();

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("결제 상태는 EXECUTING에서 FAILURE로 변경된다")
    void markFail_fromExecuting() {
        Payment payment = payment();
        payment.markExecuting();

        payment.markFail();

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILURE);
    }

    @Test
    @DisplayName("결제 상태는 EXECUTING에서 UNKNOWN으로 변경된다")
    void markUnknown_fromExecuting() {
        Payment payment = payment();
        payment.markExecuting();

        payment.markUnknown();

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.UNKNOWN);
    }

    @Test
    @DisplayName("UNKNOWN 결제는 다시 EXECUTING 후 SUCCESS로 확정될 수 있다")
    void unknown_canBeRetriedAndConfirmed() {
        Payment payment = payment();
        payment.markExecuting();
        payment.markUnknown();

        payment.markExecuting();
        payment.markSuccess();

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("SUCCESS 결제는 실패나 불명확 상태로 되돌아가지 않는다")
    void success_isTerminalForFailureAndUnknownTransitions() {
        Payment payment = payment();
        payment.markExecuting();
        payment.markSuccess();

        payment.markFail();
        payment.markUnknown();
        payment.markExecuting();

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
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
}
