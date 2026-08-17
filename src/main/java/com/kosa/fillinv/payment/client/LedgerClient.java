package com.kosa.fillinv.payment.client;

import com.kosa.fillinv.payment.client.dto.LedgerEntryRequest;
import com.kosa.fillinv.payment.client.dto.LedgerEntryResponse;
import lombok.RequiredArgsConstructor;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class LedgerClient {

    private final RestClient ledgerRestClient;

    @CircuitBreaker(name = "ledger")
    @Retryable(
            retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
            maxAttemptsExpression = "${ledger.api.retry.max-attempts:2}",
            backoff = @Backoff(delayExpression = "${ledger.api.retry.backoff-ms:100}")
    )
    public LedgerEntryResponse recordEntry(LedgerEntryRequest request) {
        return ledgerRestClient.post()
                .uri("/ledger-entry")
                .body(request)
                .retrieve()
                .body(LedgerEntryResponse.class);
    }

    @CircuitBreaker(name = "ledger")
    @Retryable(
            retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
            maxAttemptsExpression = "${ledger.api.retry.max-attempts:2}",
            backoff = @Backoff(delayExpression = "${ledger.api.retry.backoff-ms:100}")
    )
    public LedgerEntryResponse recordAdjustment(String entryId, LedgerEntryRequest request) {
        return ledgerRestClient.post()
                .uri("/ledger-entry/{entryId}/adjustment", entryId)
                .body(request)
                .retrieve()
                .body(LedgerEntryResponse.class);
    }
}
