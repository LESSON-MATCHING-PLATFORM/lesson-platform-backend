package com.kosa.fillinv.payment.integration;

import com.kosa.fillinv.payment.client.TossPaymentClient;
import com.kosa.fillinv.payment.domain.PSPConfirmationException;
import com.kosa.fillinv.payment.domain.RefundExecutionResult;
import com.kosa.fillinv.payment.domain.RefundExtraDetails;
import com.kosa.fillinv.payment.entity.Refund;
import com.kosa.fillinv.payment.entity.RefundHistory;
import com.kosa.fillinv.payment.entity.RefundStatus;
import com.kosa.fillinv.payment.outbox.PaymentOutboxEvent;
import com.kosa.fillinv.payment.outbox.PaymentOutboxRepository;
import com.kosa.fillinv.payment.outbox.PaymentOutboxStatus;
import com.kosa.fillinv.payment.repository.RefundHistoryRepository;
import com.kosa.fillinv.payment.repository.RefundRepository;
import com.kosa.fillinv.payment.service.RefundProcessor;
import com.kosa.fillinv.payment.service.dto.PGCancelCommand;
import com.kosa.fillinv.payment.service.dto.PaymentRefundResult;
import com.kosa.fillinv.booking.entity.BookingCancelReason;
import com.kosa.fillinv.booking.entity.Booking;
import com.kosa.fillinv.booking.entity.BookingStatus;
import com.kosa.fillinv.booking.repository.BookingRepository;
import com.kosa.fillinv.stock.entity.Stock;
import com.kosa.fillinv.stock.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:refund-processor-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "TOSS_SECRET_KEY=test-secret"
})
class RefundProcessorIntegrationTest {

    @MockitoBean
    private TossPaymentClient tossPaymentClient;

    @Autowired
    private RefundProcessor refundProcessor;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private RefundHistoryRepository refundHistoryRepository;

    @Autowired
    private PaymentOutboxRepository paymentOutboxRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private StockRepository stockRepository;

    @BeforeEach
    void setUp() {
        paymentOutboxRepository.deleteAll();
        refundHistoryRepository.deleteAll();
        refundRepository.deleteAll();
        bookingRepository.deleteAll();
        stockRepository.deleteAll();
    }

    @Test
    @DisplayName("환불 PG 취소 성공 시 Refund SUCCESS와 이력이 실제 DB에 저장된다")
    void processPGCancel_success_savesRefundSuccessAndHistories() {
        Refund refund = refundRepository.save(refund("refund-001"));
        PGCancelCommand command = command(refund);
        given(tossPaymentClient.cancel(any()))
                .willReturn(successResult(command));

        PaymentRefundResult result = refundProcessor.processPGCancel(command);

        Refund savedRefund = refundRepository.findById(refund.getId()).orElseThrow();
        List<RefundHistory> histories = refundHistoryRepository.findByPaymentKey(refund.getPaymentKey());
        List<PaymentOutboxEvent> outboxEvents = paymentOutboxRepository.findAll();

        assertThat(result.status()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(savedRefund.getRefundStatus()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(savedRefund.getRetryCount()).isEqualTo(1);
        assertThat(savedRefund.getTransactionKey()).isEqualTo("transaction-key-001");
        assertThat(savedRefund.getRefundedAt()).isEqualTo(Instant.parse("2026-07-28T00:00:00Z"));
        assertThat(savedRefund.getPspRaw()).isEqualTo("raw");
        assertThat(histories).extracting(RefundHistory::getNewStatus)
                .containsExactly(RefundStatus.EXECUTING, RefundStatus.SUCCESS);
        assertThat(outboxEvents).hasSize(1);
        PaymentOutboxEvent outboxEvent = outboxEvents.getFirst();
        assertThat(outboxEvent.getStatus()).isEqualTo(PaymentOutboxStatus.READY);
        assertThat(outboxEvent.getEventType()).isEqualTo("REFUND_COMPLETED");
        assertThat(outboxEvent.getAggregateId()).isEqualTo(command.refundId());
        assertThat(outboxEvent.getTopic()).isEqualTo("payment-topic");
        assertThat(outboxEvent.getPayload()).contains("\"action\":\"REFUND_COMPLETED\"");
        assertThat(outboxEvent.getPayload()).contains("\"order_id\":\"order-001\"");
    }

    @Test
    @DisplayName("환불 PG 취소 결과가 불명확하면 Refund UNKNOWN과 다음 재시도 시간이 저장된다")
    void processPGCancel_unknown_savesRefundUnknownAndNextAttemptAt() {
        Refund refund = refundRepository.save(refund("refund-001"));
        PGCancelCommand command = command(refund);
        given(tossPaymentClient.cancel(any()))
                .willThrow(unknownException());

        PaymentRefundResult result = refundProcessor.processPGCancel(command);

        Refund savedRefund = refundRepository.findById(refund.getId()).orElseThrow();
        List<RefundHistory> histories = refundHistoryRepository.findByPaymentKey(refund.getPaymentKey());
        List<PaymentOutboxEvent> outboxEvents = paymentOutboxRepository.findAll();

        assertThat(result.status()).isEqualTo(RefundStatus.UNKNOWN);
        assertThat(savedRefund.getRefundStatus()).isEqualTo(RefundStatus.UNKNOWN);
        assertThat(savedRefund.getRetryCount()).isEqualTo(1);
        assertThat(savedRefund.getNextAttemptAt()).isNotNull();
        assertThat(histories).extracting(RefundHistory::getNewStatus)
                .containsExactly(RefundStatus.EXECUTING, RefundStatus.UNKNOWN);
        assertThat(outboxEvents).isEmpty();
    }

    @Test
    @DisplayName("FAILURE 환불 재시도 성공 시 다시 EXECUTING을 거쳐 SUCCESS로 변경된다")
    void processPGCancel_failureRetrySuccess_savesRefundSuccess() {
        Refund refund = refund("refund-001");
        refund.markExecuting(Instant.parse("2026-07-28T00:00:00Z"));
        refund.markFail(Instant.parse("2026-07-28T00:01:00Z"));
        refundRepository.save(refund);

        PGCancelCommand command = command(refund);
        given(tossPaymentClient.cancel(any()))
                .willReturn(successResult(command));

        PaymentRefundResult result = refundProcessor.processPGCancel(command);

        Refund savedRefund = refundRepository.findById(refund.getId()).orElseThrow();
        List<RefundHistory> histories = refundHistoryRepository.findByPaymentKey(refund.getPaymentKey());
        List<PaymentOutboxEvent> outboxEvents = paymentOutboxRepository.findAll();

        assertThat(result.status()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(savedRefund.getRefundStatus()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(savedRefund.getRetryCount()).isEqualTo(2);
        assertThat(histories).extracting(RefundHistory::getPreviousStatus)
                .containsExactly(RefundStatus.FAILURE, RefundStatus.EXECUTING);
        assertThat(histories).extracting(RefundHistory::getNewStatus)
                .containsExactly(RefundStatus.EXECUTING, RefundStatus.SUCCESS);
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.getFirst().getEventType()).isEqualTo("REFUND_COMPLETED");
    }

    @Test
    @DisplayName("ONEDAY 환불 PG 취소 성공 시 Booking이 취소되고 availableTimeId 기준 재고가 실제 복구된다")
    void processPGCancel_success_cancelsOnedayBookingAndRestoresStock() {
        Refund refund = refundRepository.save(refund("refund-001", "booking-001"));
        Booking booking = booking(
                refund.getOrderId(),
                "ONEDAY",
                BookingStatus.APPROVAL_PENDING,
                "lesson-001",
                "available-time-001"
        );
        bookingRepository.save(booking);
        stockRepository.save(stock("stock-001", "available-time-001", 3));

        PGCancelCommand command = command(refund);
        given(tossPaymentClient.cancel(any()))
                .willReturn(successResult(command));

        PaymentRefundResult result = refundProcessor.processPGCancel(command);

        Booking savedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        Stock savedStock = stockRepository.findById("stock-001").orElseThrow();

        assertThat(result.status()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(savedBooking.getStatus()).isEqualTo(BookingStatus.CANCELED);
        assertThat(savedBooking.getCancelReason()).isEqualTo(BookingCancelReason.REFUND_COMPLETED);
        assertThat(savedBooking.getCanceledAt()).isNotNull();
        assertThat(savedStock.getQuantity()).isEqualTo(4);
    }

    @Test
    @DisplayName("STUDY 환불 PG 취소 성공 시 Booking이 취소되고 lessonId 기준 재고가 실제 복구된다")
    void processPGCancel_success_cancelsStudyBookingAndRestoresStockByLessonId() {
        Refund refund = refundRepository.save(refund("refund-001", "booking-001"));
        Booking booking = booking(
                refund.getOrderId(),
                "STUDY",
                BookingStatus.APPROVED,
                "lesson-001",
                null
        );
        bookingRepository.save(booking);
        stockRepository.save(stock("stock-001", "lesson-001", 7));

        PGCancelCommand command = command(refund);
        given(tossPaymentClient.cancel(any()))
                .willReturn(successResult(command));

        PaymentRefundResult result = refundProcessor.processPGCancel(command);

        Booking savedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        Stock savedStock = stockRepository.findById("stock-001").orElseThrow();

        assertThat(result.status()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(savedBooking.getStatus()).isEqualTo(BookingStatus.CANCELED);
        assertThat(savedBooking.getCancelReason()).isEqualTo(BookingCancelReason.REFUND_COMPLETED);
        assertThat(savedBooking.getCanceledAt()).isNotNull();
        assertThat(savedStock.getQuantity()).isEqualTo(8);
    }

    @Test
    @DisplayName("이미 CANCELED인 Booking은 환불 PG 취소 성공 후에도 재고를 다시 복구하지 않는다")
    void processPGCancel_success_whenBookingAlreadyCanceled_doesNotRestoreStockAgain() {
        Refund refund = refundRepository.save(refund("refund-001", "booking-001"));
        Booking booking = booking(
                refund.getOrderId(),
                "ONEDAY",
                BookingStatus.APPROVAL_PENDING,
                "lesson-001",
                "available-time-001"
        );
        booking.cancel(BookingCancelReason.REFUND_COMPLETED);
        bookingRepository.save(booking);
        stockRepository.save(stock("stock-001", "available-time-001", 3));

        PGCancelCommand command = command(refund);
        given(tossPaymentClient.cancel(any()))
                .willReturn(successResult(command));

        PaymentRefundResult result = refundProcessor.processPGCancel(command);

        Booking savedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        Stock savedStock = stockRepository.findById("stock-001").orElseThrow();

        assertThat(result.status()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(savedBooking.getStatus()).isEqualTo(BookingStatus.CANCELED);
        assertThat(savedBooking.getCancelReason()).isEqualTo(BookingCancelReason.REFUND_COMPLETED);
        assertThat(savedStock.getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("두 worker가 같은 환불을 동시에 처리해도 PG 취소는 한 번만 호출된다")
    void processPGCancel_whenTwoWorkersRunConcurrently_callsPgCancelOnce() throws Exception {
        Refund refund = refundRepository.save(refund("refund-001"));
        PGCancelCommand command = command(refund);
        CountDownLatch cancelStarted = new CountDownLatch(1);
        CountDownLatch releaseCancel = new CountDownLatch(1);
        doAnswer(invocation -> {
            cancelStarted.countDown();
            assertThat(releaseCancel.await(5, TimeUnit.SECONDS)).isTrue();
            return successResult(command);
        }).when(tossPaymentClient).cancel(any());

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            Future<PaymentRefundResult> first = executorService.submit(() -> refundProcessor.processPGCancel(command));
            assertThat(cancelStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<PaymentRefundResult> second = executorService.submit(() -> refundProcessor.processPGCancel(command));

            PaymentRefundResult secondResult = second.get(5, TimeUnit.SECONDS);
            releaseCancel.countDown();
            PaymentRefundResult firstResult = first.get(5, TimeUnit.SECONDS);

            assertThat(firstResult.status()).isEqualTo(RefundStatus.SUCCESS);
            assertThat(secondResult.status()).isEqualTo(RefundStatus.EXECUTING);
        } finally {
            releaseCancel.countDown();
            executorService.shutdownNow();
            assertThat(executorService.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
        }

        verify(tossPaymentClient, times(1)).cancel(any());
        Refund savedRefund = refundRepository.findById(refund.getId()).orElseThrow();
        assertThat(savedRefund.getRefundStatus()).isEqualTo(RefundStatus.SUCCESS);
    }

    private Refund refund(String refundId) {
        return refund(refundId, "order-001");
    }

    private Refund refund(String refundId, String orderId) {
        return Refund.builder()
                .id(refundId)
                .paymentId("payment-001")
                .paymentKey("payment-key-001")
                .orderId(orderId)
                .refundStatus(RefundStatus.NOT_STARTED)
                .refundAmount(1000)
                .refundReason("단순 변심")
                .build();
    }

    private Booking booking(
            String bookingId,
            String lessonType,
            BookingStatus status,
            String lessonId,
            String availableTimeId
    ) {
        return Booking.builder()
                .id(bookingId)
                .status(status)
                .requestContent("신청합니다")
                .lessonTitle("자바 레슨")
                .lessonType(lessonType)
                .lessonDescription("자바를 공부합니다")
                .lessonLocation("온라인")
                .lessonCategoryName("개발")
                .mentorNickname("멘토")
                .price(10000)
                .lessonId(lessonId)
                .menteeId("mentee-001")
                .mentorId("mentor-001")
                .optionId("option-001")
                .availableTimeId(availableTimeId)
                .build();
    }

    private Stock stock(String stockId, String serviceKey, int quantity) {
        return Stock.builder()
                .id(stockId)
                .serviceKey(serviceKey)
                .quantity(quantity)
                .build();
    }

    private PGCancelCommand command(Refund refund) {
        return new PGCancelCommand(
                refund.getId(),
                refund.getPaymentKey(),
                refund.getOrderId(),
                refund.getRefundReason(),
                refund.getRefundAmount()
        );
    }

    private RefundExecutionResult successResult(PGCancelCommand command) {
        return new RefundExecutionResult(
                command.paymentKey(),
                command.orderId(),
                new RefundExtraDetails(
                        Instant.parse("2026-07-28T00:00:00Z"),
                        command.amount(),
                        command.reason(),
                        "transaction-key-001",
                        "raw"
                )
        );
    }

    private PSPConfirmationException unknownException() {
        return PSPConfirmationException.builder()
                .errorCode("500")
                .errorMessage("환불 결과를 확인할 수 없습니다.")
                .isSuccess(false)
                .isFailure(false)
                .isUnknown(true)
                .isRetryable(true)
                .build();
    }
}
