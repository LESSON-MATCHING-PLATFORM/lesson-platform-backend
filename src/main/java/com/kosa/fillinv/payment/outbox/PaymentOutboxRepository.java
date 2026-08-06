package com.kosa.fillinv.payment.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface PaymentOutboxRepository extends JpaRepository<PaymentOutboxEvent, String> {
    List<PaymentOutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(PaymentOutboxStatus status);

    List<PaymentOutboxEvent> findTop100ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
            Collection<PaymentOutboxStatus> statuses,
            Integer retryCount
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PaymentOutboxEvent event
            set event.status = com.kosa.fillinv.payment.outbox.PaymentOutboxStatus.PROCESSING,
                event.processingStartedAt = :processingStartedAt
            where event.id = :eventId
              and event.status in :statuses
              and event.retryCount < :maxRetryCount
            """)
    int claimPublishableEvent(
            @Param("eventId") String eventId,
            @Param("statuses") Collection<PaymentOutboxStatus> statuses,
            @Param("maxRetryCount") Integer maxRetryCount,
            @Param("processingStartedAt") Instant processingStartedAt
    );
}
