package com.kosa.fillinv.payment.service;

import com.kosa.fillinv.global.exception.BusinessException;
import com.kosa.fillinv.payment.client.TossPaymentClient;
import com.kosa.fillinv.payment.client.LedgerClient;
import com.kosa.fillinv.payment.client.dto.LedgerEntryRequest;
import com.kosa.fillinv.payment.client.dto.LedgerEntryResponse;
import com.kosa.fillinv.payment.controller.dto.CheckoutCommand;
import com.kosa.fillinv.payment.controller.dto.CheckoutResult;
import com.kosa.fillinv.payment.domain.PSPConfirmationException;
import com.kosa.fillinv.payment.domain.PSPConfirmationStatus;
import com.kosa.fillinv.payment.domain.PaymentExecutionResult;
import com.kosa.fillinv.payment.domain.PaymentExtraDetails;
import com.kosa.fillinv.payment.domain.PaymentMethod;
import com.kosa.fillinv.payment.domain.PaymentType;
import com.kosa.fillinv.payment.entity.Payment;
import com.kosa.fillinv.payment.entity.PaymentStatus;
import com.kosa.fillinv.payment.outbox.PaymentOutboxService;
import com.kosa.fillinv.payment.repository.PaymentRepository;
import com.kosa.fillinv.payment.service.dto.PaymentConfirmCommand;
import com.kosa.fillinv.payment.service.dto.PaymentConfirmResult;
import com.kosa.fillinv.payment.service.dto.PaymentStatusUpdateCommand;
import com.kosa.fillinv.booking.entity.Booking;
import com.kosa.fillinv.booking.entity.BookingStatus;
import com.kosa.fillinv.booking.repository.BookingRepository;
import com.kosa.fillinv.booking.service.BookingCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentUpdateService paymentUpdateService;

    @Mock
    private TossPaymentClient tossPaymentClient;

    @Mock
    private LedgerClient ledgerClient;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingCommandService bookingCommandService;

    @Mock
    private PaymentOutboxService paymentOutboxService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private PaymentService paymentService;

    @BeforeEach
    void setUpTransactionTemplate() {
        lenient().when(transactionTemplate.execute(any()))
                .thenAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(null);
                });
        lenient().when(paymentUpdateService.tryMarkConfirmExecuting(any()))
                .thenReturn(true);
        lenient().when(ledgerClient.recordEntry(any(LedgerEntryRequest.class)))
                .thenReturn(ledgerEntryResponse());
    }

    @Test
    @DisplayName("checkout 시 Booking 스냅샷으로 Payment를 생성한다")
    void checkout_createsPaymentFromBookingSnapshot() {
        Booking booking = paymentPendingBooking();
        given(bookingRepository.findById(booking.getId()))
                .willReturn(Optional.of(booking));

        CheckoutResult result = paymentService.checkout(new CheckoutCommand(booking.getId()));

        Payment savedPayment = savedPayment();
        assertThat(savedPayment.getOrderId()).isEqualTo(booking.getId());
        assertThat(savedPayment.getOrderName()).isEqualTo("자바 멘토링 - 30분");
        assertThat(savedPayment.getBuyerId()).isEqualTo(booking.getMenteeId());
        assertThat(savedPayment.getSellerId()).isEqualTo(booking.getMentorId());
        assertThat(savedPayment.getAmount()).isEqualTo(booking.getPrice());
        assertThat(savedPayment.getPaymentStatus()).isEqualTo(PaymentStatus.NOT_STARTED);

        assertThat(result.orderId()).isEqualTo(booking.getId());
        assertThat(result.orderName()).isEqualTo(savedPayment.getOrderName());
        assertThat(result.amount()).isEqualTo(savedPayment.getAmount());
    }

    @Test
    @DisplayName("checkout 중복 요청 시 기존 Payment를 재사용하고 새로 저장하지 않는다")
    void checkout_whenPaymentAlreadyExists_reusesExistingPayment() {
        Payment existingPayment = payment();
        given(bookingRepository.findById(existingPayment.getOrderId()))
                .willReturn(Optional.of(paymentPendingBooking()));
        given(paymentRepository.findByOrderId(existingPayment.getOrderId()))
                .willReturn(Optional.of(existingPayment));

        CheckoutResult result = paymentService.checkout(new CheckoutCommand(existingPayment.getOrderId()));

        assertThat(result.orderId()).isEqualTo(existingPayment.getOrderId());
        assertThat(result.orderName()).isEqualTo(existingPayment.getOrderName());
        assertThat(result.amount()).isEqualTo(existingPayment.getAmount());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("checkout 시 Booking이 결제 대기 상태가 아니면 Payment를 생성하지 않는다")
    void checkout_whenBookingIsNotPaymentPending_throwsInvalidStatus() {
        Booking booking = booking(BookingStatus.CANCELED);
        given(bookingRepository.findById(booking.getId()))
                .willReturn(Optional.of(booking));

        assertThatThrownBy(() -> paymentService.checkout(new CheckoutCommand(booking.getId())))
                .isInstanceOf(BusinessException.class);

        verify(paymentRepository, never()).findByOrderId(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirm 성공 시 결제를 SUCCESS로 변경하고 Booking 결제 완료 처리를 호출한다")
    void confirm_success_updatesPaymentAndCompletesBookingPayment() {
        PaymentConfirmCommand command = confirmCommand();
        given(paymentRepository.findByOrderId(command.orderId())).willReturn(Optional.of(payment()));
        given(tossPaymentClient.confirm(command))
                .willReturn(successResult(command));

        PaymentConfirmResult result = paymentService.confirm(command);

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.failure()).isNull();

        verify(paymentUpdateService).tryMarkConfirmExecuting(new PaymentStatusUpdateCommand(
                command.paymentKey(),
                command.orderId(),
                PaymentStatus.EXECUTING,
                null,
                null
        ));
        verify(paymentUpdateService).updateStatus(new PaymentStatusUpdateCommand(
                command.paymentKey(),
                command.orderId(),
                PaymentStatus.SUCCESS,
                successResult(command).paymentExtraDetails(),
                null
        ));
        verify(bookingCommandService).completePayment(command.orderId());
        verify(paymentOutboxService).savePaymentCompletedEvent(command, successResult(command));

        ArgumentCaptor<LedgerEntryRequest> ledgerCaptor = ArgumentCaptor.forClass(LedgerEntryRequest.class);
        verify(ledgerClient).recordEntry(ledgerCaptor.capture());
        LedgerEntryRequest ledgerRequest = ledgerCaptor.getValue();
        assertThat(ledgerRequest.idempotencyKey()).isEqualTo("PAYMENT:payment-001:COMPLETED");
        assertThat(ledgerRequest.transactionId()).isEqualTo("payment-001");
        assertThat(ledgerRequest.orderId()).isEqualTo("booking-001");
        assertThat(ledgerRequest.userId()).isEqualTo("mentee-001");
        assertThat(ledgerRequest.accountId()).isEqualTo("mentor-001");
        assertThat(ledgerRequest.amount()).isEqualByComparingTo("30000");
        assertThat(ledgerRequest.direction()).isEqualTo("CREDIT");
    }

    @Test
    @DisplayName("이미 SUCCESS인 결제 confirm 요청은 Toss를 다시 호출하지 않고 Booking 완료를 재시도한다")
    void confirm_whenPaymentAlreadySuccess_skipsTossConfirmAndRetriesBookingCompletion() {
        PaymentConfirmCommand command = confirmCommand();
        given(paymentRepository.findByOrderId(command.orderId()))
                .willReturn(Optional.of(successPayment()));

        PaymentConfirmResult result = paymentService.confirm(command);

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.failure()).isNull();
        verify(tossPaymentClient, never()).confirm(any());
        verify(paymentUpdateService, never()).tryMarkConfirmExecuting(any());
        verify(paymentUpdateService, never()).updateStatus(any());
        verify(bookingCommandService).completePayment(command.orderId());
        verify(paymentOutboxService, never()).savePaymentCompletedEvent(any(), any());
    }

    @Test
    @DisplayName("다른 confirm 요청이 이미 EXECUTING 선점 중이면 Toss를 다시 호출하지 않고 후처리를 수행하지 않는다")
    void confirm_whenPaymentExecutingByAnotherRequest_skipsTossConfirmAndSideEffects() {
        PaymentConfirmCommand command = confirmCommand();
        given(paymentUpdateService.tryMarkConfirmExecuting(any()))
                .willReturn(false);
        given(paymentRepository.findByOrderId(command.orderId()))
                .willReturn(Optional.of(executingPayment()));

        PaymentConfirmResult result = paymentService.confirm(command);

        assertThat(result.status()).isEqualTo(PaymentStatus.EXECUTING);
        assertThat(result.failure()).isNull();
        verify(tossPaymentClient, never()).confirm(any());
        verify(paymentUpdateService, never()).updateStatus(any());
        verify(bookingCommandService, never()).completePayment(any());
        verify(paymentOutboxService, never()).savePaymentCompletedEvent(any(), any());
    }

    @Test
    @DisplayName("FAILURE 결제 confirm 재시도는 Toss를 다시 호출하고 성공 후처리를 수행한다")
    void confirm_whenPaymentFailure_retriesTossConfirmAndCompletesPayment() {
        PaymentConfirmCommand command = confirmCommand();
        given(paymentRepository.findByOrderId(command.orderId()))
                .willReturn(Optional.of(failurePayment()));
        given(tossPaymentClient.confirm(command))
                .willReturn(successResult(command));

        PaymentConfirmResult result = paymentService.confirm(command);

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.failure()).isNull();

        verify(tossPaymentClient).confirm(command);
        verify(paymentUpdateService).tryMarkConfirmExecuting(new PaymentStatusUpdateCommand(
                command.paymentKey(),
                command.orderId(),
                PaymentStatus.EXECUTING,
                null,
                null
        ));
        verify(paymentUpdateService).updateStatus(new PaymentStatusUpdateCommand(
                command.paymentKey(),
                command.orderId(),
                PaymentStatus.SUCCESS,
                successResult(command).paymentExtraDetails(),
                null
        ));
        verify(bookingCommandService).completePayment(command.orderId());
        verify(paymentOutboxService).savePaymentCompletedEvent(command, successResult(command));
    }

    @Test
    @DisplayName("UNKNOWN 결제 confirm 재시도는 Toss를 다시 호출하고 성공 후처리를 수행한다")
    void confirm_whenPaymentUnknown_retriesTossConfirmAndCompletesPayment() {
        PaymentConfirmCommand command = confirmCommand();
        given(paymentRepository.findByOrderId(command.orderId()))
                .willReturn(Optional.of(unknownPayment()));
        given(tossPaymentClient.confirm(command))
                .willReturn(successResult(command));

        PaymentConfirmResult result = paymentService.confirm(command);

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.failure()).isNull();

        verify(tossPaymentClient).confirm(command);
        verify(paymentUpdateService).tryMarkConfirmExecuting(new PaymentStatusUpdateCommand(
                command.paymentKey(),
                command.orderId(),
                PaymentStatus.EXECUTING,
                null,
                null
        ));
        verify(paymentUpdateService).updateStatus(new PaymentStatusUpdateCommand(
                command.paymentKey(),
                command.orderId(),
                PaymentStatus.SUCCESS,
                successResult(command).paymentExtraDetails(),
                null
        ));
        verify(bookingCommandService).completePayment(command.orderId());
        verify(paymentOutboxService).savePaymentCompletedEvent(command, successResult(command));
    }

    @Test
    @DisplayName("Toss 명확한 실패 시 결제를 FAILURE로 변경하고 Booking은 변경하지 않는다")
    void confirm_failure_updatesPaymentFailureOnly() {
        PaymentConfirmCommand command = confirmCommand();
        given(tossPaymentClient.confirm(command))
                .willThrow(failureException());

        PaymentConfirmResult result = paymentService.confirm(command);

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILURE);
        assertThat(result.failure().errorCode()).isEqualTo("REJECT_CARD_PAYMENT");

        PaymentStatusUpdateCommand failureCommand = lastPaymentUpdateCommand();
        assertThat(failureCommand.status()).isEqualTo(PaymentStatus.FAILURE);
        assertThat(failureCommand.failure().message()).isEqualTo("잔액 부족");
        verify(bookingCommandService, never()).completePayment(any());
        verify(paymentOutboxService, never()).savePaymentCompletedEvent(any(), any());
    }

    @Test
    @DisplayName("Toss 타임아웃 시 결제를 UNKNOWN으로 변경하고 Booking은 변경하지 않는다")
    void confirm_timeout_updatesPaymentUnknownOnly() {
        PaymentConfirmCommand command = confirmCommand();
        given(tossPaymentClient.confirm(command))
                .willThrow(new ResourceAccessException("timeout"));

        PaymentConfirmResult result = paymentService.confirm(command);

        assertThat(result.status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(result.failure().errorCode()).isEqualTo("ResourceAccessException");

        PaymentStatusUpdateCommand unknownCommand = lastPaymentUpdateCommand();
        assertThat(unknownCommand.status()).isEqualTo(PaymentStatus.UNKNOWN);
        verify(bookingCommandService, never()).completePayment(any());
        verify(paymentOutboxService, never()).savePaymentCompletedEvent(any(), any());
    }

    @Test
    @DisplayName("Ledger 기록 timeout 시 결제를 UNKNOWN으로 변경하고 완료 후처리를 수행하지 않는다")
    void confirm_whenLedgerRecordingTimesOut_updatesPaymentUnknownOnly() {
        PaymentConfirmCommand command = confirmCommand();
        given(paymentRepository.findByOrderId(command.orderId())).willReturn(Optional.of(payment()));
        given(tossPaymentClient.confirm(command))
                .willReturn(successResult(command));
        given(ledgerClient.recordEntry(any(LedgerEntryRequest.class)))
                .willThrow(new ResourceAccessException("ledger timeout"));

        PaymentConfirmResult result = paymentService.confirm(command);

        assertThat(result.status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(result.failure().errorCode()).isEqualTo("ResourceAccessException");
        assertThat(lastPaymentUpdateCommand().status()).isEqualTo(PaymentStatus.UNKNOWN);
        verify(paymentOutboxService, never()).savePaymentCompletedEvent(any(), any());
        verify(bookingCommandService, never()).completePayment(any());
    }

    @Test
    @DisplayName("Toss confirm 성공 후 DB 저장 실패 시 UNKNOWN으로 변경하고 Booking 후처리와 Outbox 저장은 수행하지 않는다")
    void confirm_whenSuccessPersistenceFails_updatesPaymentUnknownOnly() {
        PaymentConfirmCommand command = confirmCommand();
        given(paymentRepository.findByOrderId(command.orderId())).willReturn(Optional.of(payment()));
        given(tossPaymentClient.confirm(command))
                .willReturn(successResult(command));
        org.mockito.Mockito.doAnswer(invocation -> {
                    PaymentStatusUpdateCommand updateCommand = invocation.getArgument(0);
                    if (updateCommand.status() == PaymentStatus.SUCCESS) {
                        throw new DataAccessResourceFailureException("db down");
                    }
                    return null;
                })
                .when(paymentUpdateService)
                .updateStatus(any());

        PaymentConfirmResult result = paymentService.confirm(command);

        assertThat(result.status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(result.failure().errorCode()).isEqualTo("DataAccessResourceFailureException");

        PaymentStatusUpdateCommand unknownCommand = lastPaymentUpdateCommand();
        assertThat(unknownCommand.status()).isEqualTo(PaymentStatus.UNKNOWN);
        verify(bookingCommandService, never()).completePayment(any());
        verify(paymentOutboxService, never()).savePaymentCompletedEvent(any(), any());
    }

    @Test
    @DisplayName("SUCCESS 저장 실패 후 UNKNOWN 후속 저장도 실패하면 예외를 전파하지 않고 UNKNOWN을 반환한다")
    void confirm_whenSuccessAndUnknownPersistenceBothFail_returnsUnknown() {
        PaymentConfirmCommand command = confirmCommand();
        given(paymentRepository.findByOrderId(command.orderId()))
                .willReturn(Optional.of(executingPayment()));
        given(tossPaymentClient.confirm(command))
                .willReturn(successResult(command));
        org.mockito.Mockito.doAnswer(invocation -> {
                    PaymentStatusUpdateCommand updateCommand = invocation.getArgument(0);
                    if (updateCommand.status() == PaymentStatus.SUCCESS ||
                            updateCommand.status() == PaymentStatus.UNKNOWN) {
                        throw new DataAccessResourceFailureException("db down");
                    }
                    return null;
                })
                .when(paymentUpdateService)
                .updateStatus(any());

        PaymentConfirmResult result = paymentService.confirm(command);

        assertThat(result.status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(result.failure().errorCode()).isEqualTo("DataAccessResourceFailureException");

        ArgumentCaptor<PaymentStatusUpdateCommand> captor = ArgumentCaptor.forClass(PaymentStatusUpdateCommand.class);
        verify(paymentUpdateService, org.mockito.Mockito.times(2)).updateStatus(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(PaymentStatusUpdateCommand::status)
                .containsExactly(PaymentStatus.SUCCESS, PaymentStatus.UNKNOWN);
        verify(bookingCommandService, never()).completePayment(any());
        verify(paymentOutboxService, never()).savePaymentCompletedEvent(any(), any());
    }

    @Test
    @DisplayName("Ledger 기록 전 결제 상태 조회가 실패하면 UNKNOWN을 반환한다")
    void confirm_whenPaymentLookupForLedgerFails_returnsUnknownWithoutPaymentUpdate() {
        PaymentConfirmCommand command = confirmCommand();
        given(paymentRepository.findByOrderId(command.orderId()))
                .willReturn(Optional.of(executingPayment()))
                .willThrow(new DataAccessResourceFailureException("db down"));
        given(tossPaymentClient.confirm(command))
                .willReturn(successResult(command));

        PaymentConfirmResult result = paymentService.confirm(command);

        assertThat(result.status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(result.failure().errorCode()).isEqualTo("DataAccessResourceFailureException");

        verify(paymentUpdateService, never()).updateStatus(any());
        verify(bookingCommandService, never()).completePayment(any());
        verify(paymentOutboxService, never()).savePaymentCompletedEvent(any(), any());
    }

    @Test
    @DisplayName("FAILURE 결제 재시도에서 EXECUTING 선점 중 DB 실패가 발생하면 기존 상태를 유지하고 UNKNOWN 결과를 반환한다")
    void confirm_whenFailureRetryMarkExecutingFailsWithDatabaseError_returnsUnknownWithoutRerecording() {
        PaymentConfirmCommand command = confirmCommand();
        given(paymentRepository.findByOrderId(command.orderId()))
                .willReturn(Optional.of(failurePayment()));
        given(paymentUpdateService.tryMarkConfirmExecuting(any()))
                .willThrow(new DataAccessResourceFailureException("db down"));

        PaymentConfirmResult result = paymentService.confirm(command);

        assertThat(result.status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(result.failure().errorCode()).isEqualTo("DataAccessResourceFailureException");
        verify(tossPaymentClient, never()).confirm(any());
        verify(paymentUpdateService, never()).updateStatus(any());
        verify(bookingCommandService, never()).completePayment(any());
        verify(paymentOutboxService, never()).savePaymentCompletedEvent(any(), any());
    }

    @Test
    @DisplayName("UNKNOWN 결제 재시도에서 EXECUTING 선점 중 DB 실패가 발생하면 기존 상태를 유지하고 UNKNOWN 결과를 반환한다")
    void confirm_whenUnknownRetryMarkExecutingFailsWithDatabaseError_returnsUnknownWithoutRerecording() {
        PaymentConfirmCommand command = confirmCommand();
        given(paymentRepository.findByOrderId(command.orderId()))
                .willReturn(Optional.of(unknownPayment()));
        given(paymentUpdateService.tryMarkConfirmExecuting(any()))
                .willThrow(new DataAccessResourceFailureException("db down"));

        PaymentConfirmResult result = paymentService.confirm(command);

        assertThat(result.status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(result.failure().errorCode()).isEqualTo("DataAccessResourceFailureException");
        verify(tossPaymentClient, never()).confirm(any());
        verify(paymentUpdateService, never()).updateStatus(any());
        verify(bookingCommandService, never()).completePayment(any());
        verify(paymentOutboxService, never()).savePaymentCompletedEvent(any(), any());
    }

    @Test
    @DisplayName("Booking 결제 완료 처리 실패는 결제 성공 결과에 영향을 주지 않는다")
    void confirm_whenBookingCompleteFails_keepsPaymentSuccess() {
        PaymentConfirmCommand command = confirmCommand();
        given(paymentRepository.findByOrderId(command.orderId())).willReturn(Optional.of(payment()));
        given(tossPaymentClient.confirm(command))
                .willReturn(successResult(command));
        givenFailureWhenBookingComplete(command.orderId());

        PaymentConfirmResult result = paymentService.confirm(command);

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.failure()).isNull();

        PaymentStatusUpdateCommand lastCommand = lastPaymentUpdateCommand();
        assertThat(lastCommand.status()).isEqualTo(PaymentStatus.SUCCESS);
        verify(bookingCommandService).completePayment(command.orderId());
        verify(paymentOutboxService).savePaymentCompletedEvent(command, successResult(command));
    }

    private void givenFailureWhenBookingComplete(String orderId) {
        org.mockito.Mockito.doThrow(new IllegalStateException("invalid booking status"))
                .when(bookingCommandService)
                .completePayment(orderId);
    }

    private Payment savedPayment() {
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        return captor.getValue();
    }

    private PaymentStatusUpdateCommand lastPaymentUpdateCommand() {
        ArgumentCaptor<PaymentStatusUpdateCommand> captor = ArgumentCaptor.forClass(PaymentStatusUpdateCommand.class);
        verify(paymentUpdateService, org.mockito.Mockito.atLeastOnce()).updateStatus(captor.capture());
        return captor.getValue();
    }

    private PaymentConfirmCommand confirmCommand() {
        return new PaymentConfirmCommand("payment-key-001", "booking-001", 30000);
    }

    private Payment successPayment() {
        Payment payment = executingPayment();
        payment.markSuccess();
        return payment;
    }

    private Payment failurePayment() {
        Payment payment = executingPayment();
        payment.markFail();
        return payment;
    }

    private Payment unknownPayment() {
        Payment payment = executingPayment();
        payment.markUnknown();
        return payment;
    }

    private Payment executingPayment() {
        Payment payment = payment();
        payment.markExecuting();
        return payment;
    }

    private Payment payment() {
        return Payment.builder()
                .id("payment-001")
                .buyerId("mentee-001")
                .sellerId("mentor-001")
                .orderId("booking-001")
                .orderName("자바 멘토링 - 30분")
                .amount(30000)
                .build();
    }

    private PaymentExecutionResult successResult(PaymentConfirmCommand command) {
        return new PaymentExecutionResult(
                command.paymentKey(),
                command.orderId(),
                new PaymentExtraDetails(
                        PaymentType.NORMAL,
                        PaymentMethod.EASY_PAY,
                        Instant.parse("2026-07-24T05:00:00Z"),
                        "자바 멘토링 - 30분",
                        PSPConfirmationStatus.DONE,
                        command.amount().longValue(),
                        "raw"
                )
        );
    }

    private LedgerEntryResponse ledgerEntryResponse() {
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

    private PSPConfirmationException failureException() {
        return new PSPConfirmationException(
                "REJECT_CARD_PAYMENT",
                "잔액 부족",
                false,
                true,
                false,
                false
        );
    }

    private Booking paymentPendingBooking() {
        return booking(BookingStatus.PAYMENT_PENDING);
    }

    private Booking booking(BookingStatus status) {
        return Booking.builder()
                .id("booking-001")
                .status(status)
                .requestContent("멘토링 신청합니다")
                .lessonTitle("자바 멘토링")
                .lessonType("MENTORING")
                .lessonDescription("자바 백엔드 멘토링")
                .lessonLocation("ONLINE")
                .lessonCategoryName("개발")
                .mentorNickname("멘토닉")
                .optionName("30분")
                .optionMinute(30)
                .price(30000)
                .lessonId("lesson-001")
                .menteeId("mentee-001")
                .mentorId("mentor-001")
                .optionId("option-001")
                .availableTimeId(null)
                .build();
    }
}
