package com.kosa.fillinv.payment.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RefundRetryBackoffPolicyTest {

    private final RefundRetryBackoffPolicy refundRetryBackoffPolicy = new RefundRetryBackoffPolicy();

    @Test
    @DisplayName("retryCount에 따라 지수 백오프와 지터를 적용한다")
    void nextAttemptAt_appliesExponentialBackoffWithJitter() {
        Instant now = Instant.parse("2026-07-27T00:00:00Z");

        Instant nextAttemptAt = refundRetryBackoffPolicy.nextAttemptAt(now, 2);

        assertThat(nextAttemptAt).isAfterOrEqualTo(now.plusSeconds(40));
        assertThat(nextAttemptAt).isBeforeOrEqualTo(now.plusSeconds(60));
    }

    @Test
    @DisplayName("지수 백오프는 최대 지연 시간을 초과하지 않는 기준 지연을 사용한다")
    void nextAttemptAt_capsBaseDelay() {
        Instant now = Instant.parse("2026-07-27T00:00:00Z");

        Instant nextAttemptAt = refundRetryBackoffPolicy.nextAttemptAt(now, 10);

        assertThat(nextAttemptAt).isAfterOrEqualTo(now.plusSeconds(300));
        assertThat(nextAttemptAt).isBeforeOrEqualTo(now.plusSeconds(450));
    }
}
