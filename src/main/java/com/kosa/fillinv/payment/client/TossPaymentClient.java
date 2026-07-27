package com.kosa.fillinv.payment.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosa.fillinv.payment.client.dto.*;
import com.kosa.fillinv.payment.client.dto.TossPaymentConfirmRequest;
import com.kosa.fillinv.payment.domain.*;
import com.kosa.fillinv.payment.service.dto.PaymentConfirmCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;

@Component
@RequiredArgsConstructor
public class TossPaymentClient {

    private static final int MAX_RETRY_COUNT = 2;
    private final RestClient tossRestClient;
    private final ObjectMapper objectMapper;
    private final String CONFIRM_URI = "/v1/payments/confirm";
    private final String CANCEL_URI = "/v1/payments/{paymentKey}/cancel";

    public RefundExecutionResult cancel(PaymentCancelCommand command) {
        int attempt = 0;

        while (true) {
            try {
                TossPaymentConfirmationResponse response =
                        tossRestClient.post()
                                .uri(uriBuilder ->
                                        uriBuilder
                                                .path(CANCEL_URI)
                                                .build(command.paymentKey())) // paymentKey를 멱등키로 사용하여 결제 한건당 하나의 취소요청만 처리됨
                                .header("Idempotency-Key", command.paymentKey())
                                .body(new TossPaymentCancelRequest(
                                        command.cancelReason(),
                                        command.refundAmount()
                                ))
                                .retrieve()
                                .onStatus(
                                        status -> status.is4xxClientError() || status.is5xxServerError(),
                                        tossErrorHandler()
                                )
                                .body(TossPaymentConfirmationResponse.class);

                return getRefundExecutionResult(response);

            } catch (PSPConfirmationException e) {
                attempt++;
                if (!e.isRetryable() || attempt > MAX_RETRY_COUNT) {
                    throw e;
                }
                backoff(attempt);
            } catch (ResourceAccessException e) { // timeout / network
                attempt++;
                if (attempt > MAX_RETRY_COUNT) {
                    throw e;
                }
                backoff(attempt);
            }
        }
    }

    public PaymentExecutionResult confirm(PaymentConfirmCommand command) {
        int attempt = 0;

        while (true) {
            try {
                TossPaymentConfirmationResponse response =
                        tossRestClient.post()
                                .uri(CONFIRM_URI)
                                .header("Idempotency-Key", command.orderId())
                                .body(new TossPaymentConfirmRequest(
                                        command.paymentKey(),
                                        command.orderId(),
                                        command.amount()
                                ))
                                .retrieve()
                                .onStatus(
                                        status -> status.is4xxClientError() || status.is5xxServerError(),
                                        tossErrorHandler()
                                )
                                .body(TossPaymentConfirmationResponse.class);

                return getPaymentExecutionResult(response);

            } catch (PSPConfirmationException e) {
                attempt++;
                if (!e.isRetryable() || attempt > MAX_RETRY_COUNT) {
                    throw e;
                }
                backoff(attempt);
            } catch (ResourceAccessException e) { // timeout / network
                attempt++;
                if (attempt > MAX_RETRY_COUNT) {
                    throw e;
                }
                backoff(attempt);
            }
        }
    }


    private static PaymentExecutionResult getPaymentExecutionResult(TossPaymentConfirmationResponse response) {
        return new PaymentExecutionResult(
                response.paymentKey(),
                response.orderId(),
                new PaymentExtraDetails(
                        PaymentType.get(response.type().name()),
                        PaymentMethod.get(response.method()),
                        response.approvedAt().toInstant(),
                        response.orderName(),
                        PSPConfirmationStatus.get(response.status().name()),
                        response.totalAmount().longValue(),
                        response.toString()
                )
        );
    }

    private static RefundExecutionResult getRefundExecutionResult(TossPaymentConfirmationResponse response) {

        // 하나의 결제 이벤트에 대해서 여러 취소 이벤트가 있을 수 있기 때문에 리스트 형태
        // 가장 최신의 데이터를 선택
        TossPaymentConfirmationResponse.Cancel cancelEvent =
                response.cancels().stream()
                        .max(Comparator.comparing(TossPaymentConfirmationResponse.Cancel::canceledAt))
                        .orElseThrow(
                                () -> PSPConfirmationException.builder()
                                        .errorCode("400")
                                        .errorMessage("성공한 취소 요청에 취소 정보가 존재하지 않음")
                                        .isSuccess(false)
                                        .isFailure(false)
                                        .isUnknown(true)
                                        .isRetryable(true)
                                        .build()
                        ); // 결제 취소가 성공한 경우 최소 1개 이상의 cancel 데이터가 존재하야함

        return new RefundExecutionResult(
                response.paymentKey(),
                response.orderId(),
                new RefundExtraDetails(
                        cancelEvent.canceledAt().toInstant(),
                        cancelEvent.cancelAmount().intValueExact(),
                        cancelEvent.cancelReason(),
                        cancelEvent.transactionKey(),
                        response.toString()
                )
        );
    }

    private RestClient.ResponseSpec.ErrorHandler tossErrorHandler() {
        return (req, res) -> {
            try (InputStream is = res.getBody()) {
                TossPaymentConfirmationResponse.TossFailureResponse errorResponse =
                        objectMapper.readValue(is, TossPaymentConfirmationResponse.TossFailureResponse.class);

                TossPaymentError tossPaymentError = TossPaymentError.get(errorResponse.code());

                throw PSPConfirmationException.builder()
                        .errorCode(tossPaymentError.getStatusCode().toString())
                        .errorMessage(tossPaymentError.getDescription())
                        .isSuccess(tossPaymentError.isSuccess())
                        .isFailure(tossPaymentError.isFailure())
                        .isUnknown(tossPaymentError.isUnknown())
                        .isRetryable(tossPaymentError.isRetryable())
                        .build();

            } catch (IOException e) {
                throw new RuntimeException("에러 응답 파싱 실패", e);
            }
        };
    }


    private void backoff(int attempt) {
        try {
            long delayMillis = (long) (1000L * Math.pow(2, attempt));
            Thread.sleep(delayMillis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ie);
        }
    }

}
