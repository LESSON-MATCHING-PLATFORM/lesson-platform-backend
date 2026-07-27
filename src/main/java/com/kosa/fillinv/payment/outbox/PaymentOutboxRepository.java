package com.kosa.fillinv.payment.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PaymentOutboxRepository extends JpaRepository<PaymentOutboxEvent, String> {
    List<PaymentOutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(PaymentOutboxStatus status);

    List<PaymentOutboxEvent> findTop100ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
            Collection<PaymentOutboxStatus> statuses,
            Integer retryCount
    );
}
