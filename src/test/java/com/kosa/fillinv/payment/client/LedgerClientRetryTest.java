package com.kosa.fillinv.payment.client;

import com.kosa.fillinv.payment.client.dto.LedgerEntryRequest;
import com.kosa.fillinv.payment.client.dto.LedgerEntryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:ledger-retry-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never",
        "TOSS_SECRET_KEY=test-secret",
        "ledger.api.retry.max-attempts=2",
        "ledger.api.retry.backoff-ms=0",
        "resilience4j.circuitbreaker.instances.ledger.sliding-window-size=2",
        "resilience4j.circuitbreaker.instances.ledger.minimum-number-of-calls=2",
        "resilience4j.circuitbreaker.instances.ledger.failure-rate-threshold=50",
        "resilience4j.circuitbreaker.instances.ledger.wait-duration-in-open-state=1s",
        "resilience4j.circuitbreaker.instances.ledger.permitted-number-of-calls-in-half-open-state=1"
})
class LedgerClientRetryTest {

    @MockitoBean(name = "ledgerRestClient")
    private RestClient ledgerRestClient;

    private final RestClient.RequestBodyUriSpec requestSpec = mock(RestClient.RequestBodyUriSpec.class);
    private final RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
    private final RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    @Autowired
    private LedgerClient ledgerClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry.circuitBreaker("ledger").reset();
        given(ledgerRestClient.post()).willReturn(requestSpec);
        given(requestSpec.uri(anyString())).willReturn(bodySpec);
        given(bodySpec.body(any(LedgerEntryRequest.class))).willReturn(bodySpec);
        given(bodySpec.retrieve()).willReturn(responseSpec);
    }

    @Test
    @DisplayName("Ledger timeout은 설정된 횟수만큼 재시도한 뒤 성공 응답을 반환한다")
    void recordEntry_whenTimeoutThenSuccess_retriesAndReturnsResponse() {
        LedgerEntryRequest request = request();
        LedgerEntryResponse response = response();
        given(responseSpec.body(LedgerEntryResponse.class))
                .willThrow(new ResourceAccessException("timeout"))
                .willReturn(response);

        LedgerEntryResponse result = ledgerClient.recordEntry(request);

        assertThat(result).isEqualTo(response);
        verify(ledgerRestClient, times(2)).post();
    }

    @Test
    @DisplayName("Ledger 5xx 응답은 설정된 횟수만큼 재시도한 뒤 예외를 전파한다")
    void recordEntry_whenServerError_retriesAndPropagatesException() {
        LedgerEntryRequest request = request();
        given(responseSpec.body(LedgerEntryResponse.class))
                .willThrow(serverError());

        assertThatThrownBy(() -> ledgerClient.recordEntry(request))
                .isInstanceOf(HttpServerErrorException.class);

        verify(ledgerRestClient, times(2)).post();
    }

    @Test
    @DisplayName("Ledger 4xx 응답은 재시도하지 않고 예외를 전파한다")
    void recordEntry_whenClientError_doesNotRetry() {
        LedgerEntryRequest request = request();
        given(responseSpec.body(LedgerEntryResponse.class))
                .willThrow(clientError());

        assertThatThrownBy(() -> ledgerClient.recordEntry(request))
                .isInstanceOf(HttpClientErrorException.class);

        verify(ledgerRestClient, times(1)).post();
    }

    @Test
    @DisplayName("Ledger 4xx 응답이 반복되어도 Circuit은 열리지 않는다")
    void recordEntry_whenRepeatedClientErrors_keepsCircuitClosed() {
        LedgerEntryRequest request = request();
        given(responseSpec.body(LedgerEntryResponse.class))
                .willThrow(clientError());

        assertThatThrownBy(() -> ledgerClient.recordEntry(request))
                .isInstanceOf(HttpClientErrorException.class);
        assertThatThrownBy(() -> ledgerClient.recordEntry(request))
                .isInstanceOf(HttpClientErrorException.class);

        verify(ledgerRestClient, times(2)).post();
        assertThat(circuitBreakerRegistry.circuitBreaker("ledger").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("Ledger 반복 장애로 Circuit이 열리면 이후 요청을 차단한다")
    void recordEntry_whenCircuitOpens_rejectsSubsequentCalls() {
        LedgerEntryRequest request = request();
        given(responseSpec.body(LedgerEntryResponse.class))
                .willThrow(serverError());

        assertThatThrownBy(() -> ledgerClient.recordEntry(request))
                .isInstanceOf(HttpServerErrorException.class);
        assertThatThrownBy(() -> ledgerClient.recordEntry(request))
                .isInstanceOf(HttpServerErrorException.class);

        assertThatThrownBy(() -> ledgerClient.recordEntry(request))
                .isInstanceOf(CallNotPermittedException.class);

        verify(ledgerRestClient, times(4)).post();
        assertThat(circuitBreakerRegistry.circuitBreaker("ledger").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    private LedgerEntryRequest request() {
        return new LedgerEntryRequest(
                "PAYMENT:payment-001:COMPLETED",
                "PAYMENT",
                "payment-001",
                "booking-001",
                "mentee-001",
                "mentor-001",
                new BigDecimal("30000"),
                "KRW",
                "CREDIT",
                "결제 완료"
        );
    }

    private LedgerEntryResponse response() {
        return new LedgerEntryResponse(
                "ledger-entry-001",
                "PAYMENT:payment-001:COMPLETED",
                "PAYMENT",
                "payment-001",
                "booking-001",
                "mentee-001",
                "mentor-001",
                new BigDecimal("30000"),
                "KRW",
                "CREDIT",
                "POSTED",
                "결제 완료",
                Instant.parse("2026-08-16T00:00:00Z"),
                null,
                0L
        );
    }

    private HttpServerErrorException serverError() {
        return HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );
    }

    private HttpClientErrorException clientError() {
        return HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );
    }
}
