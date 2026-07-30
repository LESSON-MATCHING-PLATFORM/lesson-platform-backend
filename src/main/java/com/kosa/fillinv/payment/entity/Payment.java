package com.kosa.fillinv.payment.entity;

import com.kosa.fillinv.global.entity.BaseEntity;
import com.kosa.fillinv.payment.domain.*;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Id
    private String id;

    @Column(name = "buyer_id", nullable = false)
    private String buyerId; // mentee

    @Column(name = "seller_id", nullable = false)
    private String sellerId; // mentor

    @Column(name = "order_id", nullable = false)
    private String orderId; // 결제 품목

    @Column(name = "order_name", nullable = false)
    private String orderName;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    /* 결제 정보 */
    @Setter
    @Column(name = "payment_key")
    private String paymentKey;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Column(name = "psp_raw")
    private String pspRaw;

    @Setter
    @Column(name = "approved_at")
    private Instant approvedAt;

    @Builder
    public Payment(String id,
                   String buyerId, // mentee id
                   String sellerId, // mentor id
                   String orderId, // order id
                   String orderName,
                   Integer amount
    ) {
        this.id = id;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.orderId = orderId;
        this.orderName = orderName;
        this.amount = amount;
        this.paymentStatus = PaymentStatus.NOT_STARTED;
        this.paymentKey = null;
        this.paymentMethod = null;
        this.pspRaw = null;
        this.approvedAt = null;
    }

    public void markExecuting() {
        if (paymentStatus == PaymentStatus.NOT_STARTED ||
                paymentStatus == PaymentStatus.FAILURE ||
                paymentStatus == PaymentStatus.UNKNOWN) {
            paymentStatus = PaymentStatus.EXECUTING;
            return;
        }

        throw new IllegalStateException("EXECUTING 상태로 변경할 수 없습니다. 현재 상태: " + paymentStatus);
    }

    public void markSuccess() {
        if (paymentStatus == PaymentStatus.EXECUTING || paymentStatus == PaymentStatus.UNKNOWN) {
            paymentStatus = PaymentStatus.SUCCESS;
            return;
        }

        throw new IllegalStateException("SUCCESS 상태로 변경할 수 없습니다. 현재 상태: " + paymentStatus);
    }

    public void markFail() {
        if (paymentStatus == PaymentStatus.NOT_STARTED || paymentStatus == PaymentStatus.EXECUTING || paymentStatus == PaymentStatus.UNKNOWN) {
            paymentStatus = PaymentStatus.FAILURE;
            return;
        }

        throw new IllegalStateException("FAILURE 상태로 변경할 수 없습니다. 현재 상태: " + paymentStatus);
    }

    // 실제 운영 시에는 전체 raw 데이터를 저장 필수. 테스트를 위해서 길이 제한
    public void setPspRaw(String pspRaw) {
        this.pspRaw = pspRaw.substring(0, Math.min(pspRaw.length(), 50));
    }

    public void markUnknown() {
        if (paymentStatus == PaymentStatus.NOT_STARTED || paymentStatus == PaymentStatus.EXECUTING) {
            paymentStatus = PaymentStatus.UNKNOWN;
            return;
        }

        throw new IllegalStateException("UNKNOWN 상태로 변경할 수 없습니다. 현재 상태: " + paymentStatus);
    }
}
