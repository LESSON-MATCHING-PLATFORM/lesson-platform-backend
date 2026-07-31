package com.kosa.fillinv.payment.application;

import com.kosa.fillinv.booking.entity.BookingStatus;
import com.kosa.fillinv.booking.service.BookingCommandService;
import com.kosa.fillinv.payment.entity.Payment;
import com.kosa.fillinv.payment.entity.PaymentStatus;
import com.kosa.fillinv.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentBookingCompletionRetryService {

    private static final int BATCH_SIZE = 50;

    private final PaymentRepository paymentRepository;
    private final BookingCommandService bookingCommandService;

    public void retryPendingBookingCompletions() {
        List<Payment> payments = paymentRepository.findByPaymentStatusAndBookingStatus(
                PaymentStatus.SUCCESS,
                BookingStatus.PAYMENT_PENDING,
                PageRequest.of(0, BATCH_SIZE)
        );

        for (Payment payment : payments) {
            retryOne(payment);
        }
    }

    private void retryOne(Payment payment) {
        try {
            bookingCommandService.completePayment(payment.getOrderId());
        } catch (Exception e) {
            log.error("Payment succeeded, but booking payment completion retry failed. orderId={}", payment.getOrderId(), e);
        }
    }
}
