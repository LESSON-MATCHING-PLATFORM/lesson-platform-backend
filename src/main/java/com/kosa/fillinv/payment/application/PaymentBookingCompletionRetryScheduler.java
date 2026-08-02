package com.kosa.fillinv.payment.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentBookingCompletionRetryScheduler {

    private final PaymentBookingCompletionRetryService retryService;

    @Scheduled(fixedDelayString = "${payment.booking-completion.retry-fixed-delay-ms:10000}")
    public void retryPendingBookingCompletions() {
        retryService.retryPendingBookingCompletions();
    }
}
