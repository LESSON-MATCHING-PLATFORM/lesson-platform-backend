package com.kosa.fillinv.payment.entity;

public enum RefundStatus {
    NOT_STARTED("환불 승인 시작 전"),
    EXECUTING("환불 승인 중"),
    SUCCESS("환불 승인 성공"),
    FAILURE("환불 승인 실패"),
    UNKNOWN("환불 승인 결과 알 수 없는 상태");

    private String description;

    RefundStatus(String description) {
        this.description = description;
    }

    public static RefundStatus fromName(String name) {
        for (RefundStatus status : RefundStatus.values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        throw new IllegalArgumentException("RefundStatus: " + name + "는 올바르지 않은 결제 타입입니다.");
    }

    public String getDescription() {
        return description;
    }
}
