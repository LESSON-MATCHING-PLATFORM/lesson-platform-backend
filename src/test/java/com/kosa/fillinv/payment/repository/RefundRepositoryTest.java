package com.kosa.fillinv.payment.repository;

import com.kosa.fillinv.payment.entity.Refund;
import com.kosa.fillinv.payment.entity.RefundStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class RefundRepositoryTest {

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("재시도 대상 UNKNOWN과 FAILURE 환불만 조회한다")
    void findRetryableRefunds() {
        Instant now = Instant.parse("2026-07-27T00:00:00Z");
        Refund unknown = unknownRefund("unknown", 2, now.minusSeconds(10));
        Refund failure = failureRefund("failure", 2, now.minusSeconds(5));
        Refund futureUnknown = unknownRefund("future-unknown", 2, now.plusSeconds(1));
        Refund exhaustedUnknown = unknownRefund("exhausted-unknown", 3, now.minusSeconds(1));
        Refund futureFailure = failureRefund("future-failure", 2, now.plusSeconds(1));
        Refund exhaustedFailure = failureRefund("exhausted-failure", 3, now.minusSeconds(1));
        Refund success = successRefund("success", 2);

        refundRepository.saveAll(List.of(
                unknown,
                failure,
                futureUnknown,
                exhaustedUnknown,
                futureFailure,
                exhaustedFailure,
                success
        ));

        entityManager.flush();
        entityManager.clear();

        List<Refund> refunds = refundRepository
                .findTop100ByRefundStatusInAndRetryCountLessThanAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                        List.of(RefundStatus.UNKNOWN, RefundStatus.FAILURE),
                        3,
                        now
                );

        assertThat(refunds)
                .extracting(Refund::getId)
                .containsExactly("unknown", "failure");
    }

    private Refund unknownRefund(String id, int attemptCount, Instant nextAttemptAt) {
        Refund refund = refund(id);
        makeUnknownAttempts(refund, attemptCount, nextAttemptAt);

        return refund;
    }

    private Refund failureRefund(String id, int attemptCount, Instant nextAttemptAt) {
        Refund refund = refund(id);
        makeUnknownAttempts(refund, attemptCount - 1, nextAttemptAt);
        refund.markExecuting(Instant.parse("2026-07-27T00:00:00Z").plusSeconds(attemptCount));
        refund.markFail(nextAttemptAt);

        return refund;
    }

    private Refund successRefund(String id, int attemptCount) {
        Refund refund = refund(id);
        makeUnknownAttempts(refund, attemptCount - 1, Instant.parse("2026-07-27T00:00:00Z"));
        refund.markExecuting(Instant.parse("2026-07-27T00:00:00Z").plusSeconds(attemptCount));
        refund.markSuccess("transaction-" + id, Instant.parse("2026-07-27T00:00:00Z"), "raw");

        return refund;
    }

    private Refund refund(String id) {
        Refund refund = Refund.builder()
                .id(id)
                .paymentId("payment-" + id)
                .paymentKey("payment-key-" + id)
                .orderId("order-" + id)
                .refundStatus(RefundStatus.NOT_STARTED)
                .refundAmount(1000)
                .refundReason("refund reason")
                .build();

        return refund;
    }

    private void makeUnknownAttempts(Refund refund, int attemptCount, Instant nextAttemptAt) {
        for (int i = 0; i < attemptCount; i++) {
            refund.markExecuting(Instant.parse("2026-07-27T00:00:00Z").plusSeconds(i));
            refund.markUnknown(nextAttemptAt);
        }
    }
}
