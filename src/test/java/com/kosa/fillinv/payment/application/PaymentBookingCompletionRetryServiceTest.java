package com.kosa.fillinv.payment.application;

import com.kosa.fillinv.booking.entity.BookingStatus;
import com.kosa.fillinv.booking.service.BookingCommandService;
import com.kosa.fillinv.payment.entity.Payment;
import com.kosa.fillinv.payment.entity.PaymentStatus;
import com.kosa.fillinv.payment.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentBookingCompletionRetryServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private BookingCommandService bookingCommandService;

    @InjectMocks
    private PaymentBookingCompletionRetryService retryService;

    @Test
    @DisplayName("SUCCESS Payment인데 Booking이 PAYMENT_PENDING이면 결제 완료 처리를 재시도한다")
    void retryPendingBookingCompletions_retriesSuccessPaymentsWithPendingBooking() {
        Payment payment = payment("booking-001");
        when(paymentRepository.findByPaymentStatusAndBookingStatus(
                PaymentStatus.SUCCESS,
                BookingStatus.PAYMENT_PENDING,
                PageRequest.of(0, 50)
        )).thenReturn(List.of(payment));

        retryService.retryPendingBookingCompletions();

        verify(bookingCommandService).completePayment("booking-001");
    }

    @Test
    @DisplayName("Booking 결제 완료 재시도 중 한 건이 실패해도 다음 항목을 계속 처리한다")
    void retryPendingBookingCompletions_whenOneFails_continuesNextPayment() {
        Payment first = payment("booking-001");
        Payment second = payment("booking-002");
        when(paymentRepository.findByPaymentStatusAndBookingStatus(any(), any(), any()))
                .thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("retry failed"))
                .when(bookingCommandService)
                .completePayment("booking-001");

        retryService.retryPendingBookingCompletions();

        verify(bookingCommandService).completePayment("booking-001");
        verify(bookingCommandService).completePayment("booking-002");
    }

    private Payment payment(String orderId) {
        Payment payment = Payment.builder()
                .id("payment-" + orderId)
                .buyerId("mentee-001")
                .sellerId("mentor-001")
                .orderId(orderId)
                .orderName("자바 멘토링")
                .amount(30000)
                .build();
        payment.markExecuting();
        payment.markSuccess();
        return payment;
    }
}
