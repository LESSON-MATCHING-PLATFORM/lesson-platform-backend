package com.kosa.fillinv.payment.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor // 역직렬화에 필수
@ToString
public class PaymentEvent {
    @JsonProperty("user_id")
    private String userId;
    @JsonProperty("user_name")
    private String userName;
    @JsonProperty("action")
    private String action;
    @JsonProperty("amount")
    private String amount;
    @JsonProperty("order_id")
    private String orderId;
    @JsonProperty("timestamp")
    private String timestamp;
    @JsonProperty("value")
    private String value;
}
