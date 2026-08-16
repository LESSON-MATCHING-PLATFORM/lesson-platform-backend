package com.kosa.fillinv.payment.client;

import com.kosa.fillinv.payment.client.dto.LedgerEntryRequest;
import com.kosa.fillinv.payment.client.dto.LedgerEntryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class LedgerClient {

    private final RestClient ledgerRestClient;

    public LedgerEntryResponse recordEntry(LedgerEntryRequest request) {
        return ledgerRestClient.post()
                .uri("/ledger-entry")
                .body(request)
                .retrieve()
                .body(LedgerEntryResponse.class);
    }

    public LedgerEntryResponse recordAdjustment(String entryId, LedgerEntryRequest request) {
        return ledgerRestClient.post()
                .uri("/ledger-entry/{entryId}/adjustment", entryId)
                .body(request)
                .retrieve()
                .body(LedgerEntryResponse.class);
    }
}
