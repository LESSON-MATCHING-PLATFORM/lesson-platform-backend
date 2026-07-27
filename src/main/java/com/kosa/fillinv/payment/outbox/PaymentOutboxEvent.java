package com.kosa.fillinv.payment.outbox;

import com.kosa.fillinv.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "payment_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentOutboxEvent extends BaseEntity {

    @Id
    @Column(name = "event_id")
    private String id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentOutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Builder
    public PaymentOutboxEvent(String id, String eventType, String aggregateId, String topic, String payload) {
        this.id = id;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.payload = payload;
        this.status = PaymentOutboxStatus.READY;
        this.retryCount = 0;
    }

    public void markPublished(Instant publishedAt) {
        this.status = PaymentOutboxStatus.PUBLISHED;
        this.publishedAt = publishedAt;
        this.lastError = null;
    }

    public void markFailed(String lastError) {
        this.status = PaymentOutboxStatus.FAILED;
        this.retryCount++;
        this.lastError = lastError;
    }
}
