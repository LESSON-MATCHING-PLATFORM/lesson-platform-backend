package com.kosa.fillinv.payment.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosa.fillinv.payment.domain.PSPConfirmationStatus;
import com.kosa.fillinv.payment.domain.PaymentExecutionResult;
import com.kosa.fillinv.payment.domain.PaymentExtraDetails;
import com.kosa.fillinv.payment.domain.PaymentMethod;
import com.kosa.fillinv.payment.domain.PaymentType;
import com.kosa.fillinv.payment.service.dto.PaymentConfirmCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentOutboxServiceTest {

    @Mock
    private PaymentOutboxRepository paymentOutboxRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentOutboxService paymentOutboxService;

    @Test
    @DisplayName("결제 완료 이벤트를 READY 상태 Outbox 이벤트로 저장한다")
    void savePaymentCompletedEvent() throws Exception {
        PaymentConfirmCommand command = command();
        PaymentExecutionResult result = result(command);
        String payload = "{\"action\":\"PAYMENT_COMPLETED\",\"order_id\":\"schedule-001\"}";
        given(objectMapper.writeValueAsString(any()))
                .willReturn(payload);

        paymentOutboxService.savePaymentCompletedEvent(command, result);

        PaymentOutboxEvent saved = savedEvent();
        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getEventType()).isEqualTo("PAYMENT_COMPLETED");
        assertThat(saved.getAggregateId()).isEqualTo(command.orderId());
        assertThat(saved.getTopic()).isEqualTo("payment-topic");
        assertThat(saved.getPayload()).isEqualTo(payload);
        assertThat(saved.getStatus()).isEqualTo(PaymentOutboxStatus.READY);
        assertThat(saved.getRetryCount()).isZero();
    }

    @Test
    @DisplayName("결제 완료 이벤트 직렬화 실패 시 예외를 던진다")
    void savePaymentCompletedEvent_whenSerializationFails() throws Exception {
        PaymentConfirmCommand command = command();
        given(objectMapper.writeValueAsString(any()))
                .willThrow(new JsonProcessingException("boom") {
                });

        assertThatThrownBy(() -> paymentOutboxService.savePaymentCompletedEvent(command, result(command)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Payment outbox event serialization failed");
    }

    private PaymentOutboxEvent savedEvent() {
        ArgumentCaptor<PaymentOutboxEvent> captor = ArgumentCaptor.forClass(PaymentOutboxEvent.class);
        verify(paymentOutboxRepository).save(captor.capture());
        return captor.getValue();
    }

    private PaymentConfirmCommand command() {
        return new PaymentConfirmCommand("payment-key-001", "schedule-001", 30000);
    }

    private PaymentExecutionResult result(PaymentConfirmCommand command) {
        return new PaymentExecutionResult(
                command.paymentKey(),
                command.orderId(),
                new PaymentExtraDetails(
                        PaymentType.NORMAL,
                        PaymentMethod.EASY_PAY,
                        Instant.parse("2026-07-24T05:00:00Z"),
                        "자바 멘토링 - 30분",
                        PSPConfirmationStatus.DONE,
                        command.amount().longValue(),
                        "raw"
                )
        );
    }
}
