package com.kosa.fillinv.payment.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosa.fillinv.payment.client.dto.PaymentCancelCommand;
import com.kosa.fillinv.payment.client.dto.TossPaymentCancelRequest;
import com.kosa.fillinv.payment.client.dto.TossPaymentConfirmRequest;
import com.kosa.fillinv.payment.domain.PSPConfirmationException;
import com.kosa.fillinv.payment.service.dto.PaymentConfirmCommand;
import com.kosa.fillinv.payment.service.dto.PaymentRefundCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("local")
class TossPaymentClientRetryTest {

    @MockitoBean
    private RestClient tossRestClient;

    @MockitoBean
    private ObjectMapper objectMapper;

    @MockitoBean
    private RestClient.RequestBodyUriSpec requestSpec;

    @MockitoBean
    private RestClient.RequestBodySpec bodySpec;

    @MockitoBean
    private RestClient.ResponseSpec responseSpec;

    /**
     * Spy를 사용하는 이유:
     * - confirm() 실제 로직은 그대로 실행
     * - 내부 호출 횟수 검증 가능
     */
    @MockitoSpyBean
    private TossPaymentClient tossPaymentClient;

    @BeforeEach
    void setUp() {
        // RestClient 체이닝 구성
        when(tossRestClient.post()).thenReturn(requestSpec);
        when(requestSpec.uri(Mockito.<Function<UriBuilder, URI>>any())).thenReturn(bodySpec);
        when(requestSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec);
        when(bodySpec.body(any(TossPaymentConfirmRequest.class))).thenReturn(bodySpec);
        when(bodySpec.body(any(TossPaymentCancelRequest.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    @DisplayName("TOSS confirm 과정에서 재시도 가능한 실패의 경우 backoff 전략이 실행된다")
    void confirm_retryable_error_should_retry_and_call_post_three_times() {
        // given
        PaymentConfirmCommand command =
                new PaymentConfirmCommand("order-001", "payment-key", 1000);

        // onStatus에서 항상 retryable 예외 발생시키기
        when(responseSpec.onStatus(any(), any()))
                .thenAnswer(invocation -> {
                    throw PSPConfirmationException.builder()
                            .isRetryable(true)
                            .isUnknown(true)
                            .build();
                });

        // when & then
        assertThatThrownBy(() -> tossPaymentClient.confirm(command))
                .isInstanceOf(PSPConfirmationException.class);

        // 최초 1회 + retry 2회 = 총 3번
        verify(tossRestClient, times(3)).post();
    }

    @Test
    @DisplayName("TOSS cancel 과정에서 재시도 가능한 실패의 경우 backoff 전략이 실행된다")
    void refund_retryable_error_should_retry_and_call_post_three_times() {
        // given
        PaymentCancelCommand command =
                new PaymentCancelCommand("paymentKey", "orderId", "단순변심", 1000);

        // onStatus에서 항상 retryable 예외 발생시키기
        when(responseSpec.onStatus(any(), any()))
                .thenAnswer(invocation -> {
                    throw PSPConfirmationException.builder()
                            .isRetryable(true)
                            .isUnknown(true)
                            .build();
                });

        // when & then
        assertThatThrownBy(() -> tossPaymentClient.cancel(command))
                .isInstanceOf(PSPConfirmationException.class);

        // 최초 1회 + retry 2회 = 총 3번
        verify(tossRestClient, times(3)).post();
    }

}