package com.kosa.fillinv.payment.client;

import com.kosa.fillinv.payment.client.dto.PaymentCancelCommand;
import com.kosa.fillinv.payment.domain.RefundExecutionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class TossPaymentClientTest {

    @Autowired
    private TossPaymentClient tossPaymentClient;

    @Test // 로컬에서 수동 테스트 용도
    @DisplayName("결제된 요청을 취소한다.")
    void refund() {
        // given
        String paymentKey = "payment-key";
        String refundReason = "refund-reason";
        String orderId = "order-001";
        Integer refundAmount = 1000;

        // when
        RefundExecutionResult result =  tossPaymentClient.cancel(new PaymentCancelCommand(paymentKey, orderId, refundReason, refundAmount));

        // then
        assertThat(result.paymentKey()).isEqualTo(paymentKey);
        assertThat(result.orderId()).isNotEmpty();
        assertThat(result.refundExtraDetails().refundedAt()).isNotNull();
        assertThat(result.refundExtraDetails().refundReason()).isEqualTo(refundReason);
        assertThat(result.refundExtraDetails().refundAmount()).isEqualTo(refundAmount);
        assertThat(result.refundExtraDetails().transactionKey()).isNotEmpty();
        assertThat(result.refundExtraDetails().pspRawData()).isNotEmpty();
    }
}