package com.kosa.fillinv.payment.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
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

    @Query("""
            select event
            from PaymentOutboxEvent event
            where event.retryCount < :maxRetryCount
              and (
                    event.status in :statuses
                    or (
                        event.status = com.kosa.fillinv.payment.outbox.PaymentOutboxStatus.PROCESSING
                        and event.processingStartedAt < :staleBefore
                    )
              )
            order by event.createdAt asc
            """)
    List<PaymentOutboxEvent> findPublishableEvents(
            @Param("statuses") Collection<PaymentOutboxStatus> statuses,
            @Param("maxRetryCount") Integer maxRetryCount,
            @Param("staleBefore") Instant staleBefore,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PaymentOutboxEvent event
            set event.status = com.kosa.fillinv.payment.outbox.PaymentOutboxStatus.PROCESSING,
                event.processingStartedAt = :processingStartedAt
            where event.id = :eventId
              and event.retryCount < :maxRetryCount
              and (
                    event.status in :statuses
                    or (
                        event.status = com.kosa.fillinv.payment.outbox.PaymentOutboxStatus.PROCESSING
                        and event.processingStartedAt < :staleBefore
                    )
              )
            """)
    int claimPublishableEvent(
            @Param("eventId") String eventId,
            @Param("statuses") Collection<PaymentOutboxStatus> statuses,
            @Param("maxRetryCount") Integer maxRetryCount,
            @Param("staleBefore") Instant staleBefore,
            @Param("processingStartedAt") Instant processingStartedAt
    );

    boolean existsByIdAndStatusAndProcessingStartedAt(
            String id,
            PaymentOutboxStatus status,
            Instant processingStartedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PaymentOutboxEvent event
            set event.status = com.kosa.fillinv.payment.outbox.PaymentOutboxStatus.PUBLISHED,
                event.publishedAt = :publishedAt,
                event.processingStartedAt = null,
                event.lastError = null
            where event.id = :eventId
              and event.status = com.kosa.fillinv.payment.outbox.PaymentOutboxStatus.PROCESSING
              and event.processingStartedAt = :processingStartedAt
            """)
    int markPublishedIfProcessingClaim(
            @Param("eventId") String eventId,
            @Param("processingStartedAt") Instant processingStartedAt,
            @Param("publishedAt") Instant publishedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PaymentOutboxEvent event
            set event.status = com.kosa.fillinv.payment.outbox.PaymentOutboxStatus.FAILED,
                event.retryCount = event.retryCount + 1,
                event.processingStartedAt = null,
                event.lastError = :lastError
            where event.id = :eventId
              and event.status = com.kosa.fillinv.payment.outbox.PaymentOutboxStatus.PROCESSING
              and event.processingStartedAt = :processingStartedAt
            """)
    int markFailedIfProcessingClaim(
            @Param("eventId") String eventId,
            @Param("processingStartedAt") Instant processingStartedAt,
            @Param("lastError") String lastError
    );
}
