package com.kosa.fillinv.payment.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PaymentEvent(
        @JsonProperty("user_id")
        String userId,

        @JsonProperty("user_name")
        String userName,

        @JsonProperty("action")
        String action,

        @JsonProperty("amount")
        String amount,

        @JsonProperty("order_id")
        String orderId,

        @JsonProperty("timestamp")
        String timestamp,

        @JsonProperty("value")
        String value
) {
}
