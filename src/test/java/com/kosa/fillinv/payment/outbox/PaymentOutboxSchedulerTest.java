package com.kosa.fillinv.payment.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentOutboxSchedulerTest {

    @Mock
    private PaymentOutboxPublisher paymentOutboxPublisher;

    @InjectMocks
    private PaymentOutboxScheduler paymentOutboxScheduler;

    @Test
    @DisplayName("Outbox 스케줄러는 발행 대상 이벤트 처리를 Publisher에 위임한다")
    void publishPaymentOutboxEvents_delegatesToPublisher() {
        paymentOutboxScheduler.publishPaymentOutboxEvents();

        verify(paymentOutboxPublisher).publishReadyEvents();
    }
}
