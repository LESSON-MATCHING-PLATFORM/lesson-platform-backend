package com.kosa.fillinv.payment.service;

import com.kosa.fillinv.payment.entity.Refund;
import com.kosa.fillinv.payment.entity.RefundStatus;
import com.kosa.fillinv.payment.repository.RefundRepository;
import com.kosa.fillinv.booking.entity.BookingStatus;
import com.kosa.fillinv.booking.service.BookingCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundInternalStateRecoveryServiceTest {

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private BookingCommandService bookingCommandService;

    @InjectMocks
    private RefundInternalStateRecoveryService refundInternalStateRecoveryService;

    @Test
    @DisplayName("Refund SUCCESS이고 Booking이 취소 가능 상태인 항목을 조회한다")
    void recoverRefundInternalStates_queriesSuccessfulRefundsWithCancelableBookings() {
        given(refundRepository.findRefundsPendingInternalStateRecovery(any(), any(), any()))
                .willReturn(List.of());

        refundInternalStateRecoveryService.recoverRefundInternalStates();

        verify(refundRepository).findRefundsPendingInternalStateRecovery(
                eq(RefundStatus.SUCCESS),
                eq(List.of(BookingStatus.APPROVAL_PENDING, BookingStatus.APPROVED)),
                any(Pageable.class)
        );
    }

    @Test
    @DisplayName("조회된 환불의 orderId로 Booking 환불 취소를 재처리한다")
    void recoverRefundInternalStates_cancelsBookingsByRefund() {
        Refund refund = refund("refund-001", "booking-001");
        given(refundRepository.findRefundsPendingInternalStateRecovery(any(), any(), any()))
                .willReturn(List.of(refund));

        refundInternalStateRecoveryService.recoverRefundInternalStates();

        verify(bookingCommandService).cancelByRefund("booking-001");
    }

    @Test
    @DisplayName("한 항목의 재처리 실패가 나머지 항목 처리를 막지 않는다")
    void recoverRefundInternalStates_whenOneRecoveryFails_continuesRemainingRecoveries() {
        Refund first = refund("refund-001", "booking-001");
        Refund second = refund("refund-002", "booking-002");
        given(refundRepository.findRefundsPendingInternalStateRecovery(any(), any(), any()))
                .willReturn(List.of(first, second));
        doThrow(new IllegalStateException("cancel failed"))
                .when(bookingCommandService)
                .cancelByRefund("booking-001");

        refundInternalStateRecoveryService.recoverRefundInternalStates();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(bookingCommandService, times(2)).cancelByRefund(captor.capture());
        assertThat(captor.getAllValues())
                .containsExactly("booking-001", "booking-002");
    }

    private Refund refund(String refundId, String orderId) {
        Refund refund = Refund.builder()
                .id(refundId)
                .paymentId("payment-" + refundId)
                .paymentKey("payment-key-" + refundId)
                .orderId(orderId)
                .refundStatus(RefundStatus.NOT_STARTED)
                .refundAmount(1000)
                .refundReason("단순 변심")
                .build();
        refund.markExecuting(java.time.Instant.parse("2026-07-31T00:00:00Z"));
        refund.markSuccess("transaction-" + refundId, java.time.Instant.parse("2026-07-31T00:00:01Z"), "raw");
        return refund;
    }
}
