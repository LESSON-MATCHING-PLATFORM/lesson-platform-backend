package com.kosa.fillinv.payment.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class RefundRetryBackoffPolicy {

    private static final long BASE_DELAY_SECONDS = 10L;
    private static final long MAX_DELAY_SECONDS = 300L;

    public Instant nextAttemptAt(Instant now, int retryCount) {
        long exponentialDelay = BASE_DELAY_SECONDS * (1L << retryCount);
        long cappedDelay = Math.min(exponentialDelay, MAX_DELAY_SECONDS);
        long jitter = ThreadLocalRandom.current().nextLong(cappedDelay / 2 + 1);

        return now.plusSeconds(cappedDelay + jitter);
    }
}
