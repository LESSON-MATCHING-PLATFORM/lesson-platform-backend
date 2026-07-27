package com.kosa.fillinv.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString
@Table(name = "refund_history")
public class RefundHistory {

    @Id
    @Column(name = "refund_history_id")
    private String id;

    @Column(name = "payment_key")
    private String paymentKey;

    @Column(name = "previous_status")
    private RefundStatus previousStatus;

    @Column(name = "new_status")
    private RefundStatus newStatus;

    @Column(name = "reason")
    private String reason;

    @Builder
    public RefundHistory(String id,
                         String paymentKey,
                         RefundStatus previousStatus,
                         RefundStatus newStatus,
                         String reason) {
        this.id = id;
        this.paymentKey = paymentKey;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reason = reason;
    }
}
