package com.kosa.fillinv.payment.service;

import com.kosa.fillinv.global.exception.ResourceException;
import com.kosa.fillinv.global.exception.BusinessException;
import com.kosa.fillinv.global.response.ErrorCode;
import com.kosa.fillinv.payment.client.TossPaymentClient;
import com.kosa.fillinv.payment.client.LedgerClient;
import com.kosa.fillinv.payment.client.dto.LedgerEntryRequest;
import com.kosa.fillinv.payment.client.dto.LedgerEntryResponse;
import com.kosa.fillinv.payment.controller.dto.CheckoutCommand;
import com.kosa.fillinv.payment.controller.dto.CheckoutResult;
import com.kosa.fillinv.payment.domain.PSPConfirmationException;
import com.kosa.fillinv.payment.domain.PaymentExecutionResult;
import com.kosa.fillinv.payment.domain.PaymentFailure;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.sql.SQLException;
import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentUpdateService paymentUpdateService;
    private final TossPaymentClient tossPaymentClient;
    private final LedgerClient ledgerClient;
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final BookingCommandService bookingCommandService;
    private final PaymentOutboxService paymentOutboxService;
    private final TransactionTemplate transactionTemplate;

    /*
     * Booking에 대한 Payment 객체를 생성 및 데이터베이스에 저장
     * Payment 객체를 통해서 이후 결제 과정에서 상태를 관리
     * */
    @Transactional
    public CheckoutResult checkout(CheckoutCommand command) {

        String bookingId = command.scheduleId();

        // 결제할 Booking 정보 조회
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceException.NotFound("Booking을 찾을 수 없습니다. bookingId: " + bookingId));
        validateCheckoutReady(booking);

        Payment existingPayment = paymentRepository.findByOrderId(bookingId)
                .orElse(null);
        if (existingPayment != null) {
            return new CheckoutResult(
                    existingPayment.getOrderId(),
                    existingPayment.getOrderName(),
                    existingPayment.getAmount()
            );
        }

        Integer amount = booking.getPrice();
        String orderName = booking.getLessonTitle() + (booking.getOptionName() != null ? " - " + booking.getOptionName() : "");

        // 결제 준비
        Payment initPayment = Payment.builder()
                .id(UUID.randomUUID().toString())
                .orderId(bookingId)
                .orderName(orderName)
                .buyerId(booking.getMenteeId())
                .sellerId(booking.getMentorId())
                .amount(amount)
                .build();

        paymentRepository.save(initPayment);

        return new CheckoutResult(command.scheduleId(), orderName, amount);
    }

    /*
     * TOSS에 결제 확인을 요청을 하는 메소드
     * 상태를 추적하기 위해 상태변경 시 PaymentHistory를 함께 저장
     * */
    public PaymentConfirmResult confirm(PaymentConfirmCommand command) {
        try {
            if (isAlreadySucceeded(command.orderId())) {
                completeBookingPayment(command.orderId());
                return new PaymentConfirmResult(PaymentStatus.SUCCESS, null);
            }

            if (!markPaymentExecuting(command)) {
                return handleConfirmExecutionAlreadyClaimed(command.orderId());
            }

            PaymentExecutionResult result = tossPaymentClient.confirm(command);

            recordPaymentLedger(command, result);
            completePaymentSuccess(command, result);
            completeBookingPayment(command.orderId());

            return new PaymentConfirmResult(
                    PaymentStatus.SUCCESS,
                    null
            );
        } catch (Exception e) {
            // 결제 상태 실패 또는 알수없음으로 변경
            return handlePaymentError(command, e);
        }
    }

    public PaymentConfirmResult handlePaymentError(PaymentConfirmCommand command, Throwable e) {
        PaymentStatus status;
        PaymentFailure failure;

        if (e instanceof PSPConfirmationException) {
            status = ((PSPConfirmationException) e).paymentStatus();
            failure = new PaymentFailure(((PSPConfirmationException) e).getErrorCode(), e.getMessage());
        } else if (isDatabaseFailure(e)) {
            status = PaymentStatus.UNKNOWN;
            failure = new PaymentFailure(e.getClass().getSimpleName(), e.getMessage() == null ? "" : e.getMessage());
        } else if (e instanceof ResourceAccessException) { // time out or network
            status = PaymentStatus.UNKNOWN;
            failure = new PaymentFailure(e.getClass().getSimpleName(), e.getMessage() == null ? "" : e.getMessage());
        } else if (e instanceof org.springframework.web.client.RestClientException) {
            status = PaymentStatus.UNKNOWN;
            failure = new PaymentFailure(e.getClass().getSimpleName(), e.getMessage() == null ? "" : e.getMessage());
        } else {
            status = PaymentStatus.FAILURE;
            failure = new PaymentFailure(e.getClass().getSimpleName(), e.getMessage() == null ? "" : e.getMessage());
        }

        if (status == PaymentStatus.UNKNOWN) {
            markPaymentUnknownBestEffort(command, failure, e);
        } else {
            markPaymentFailedOrUnknown(command, status, failure);
        }

        return new PaymentConfirmResult(status, failure);
    }

    private boolean isDatabaseFailure(Throwable e) {
        return e instanceof SQLException ||
                e instanceof DataAccessException ||
                e instanceof TransactionException ||
                e instanceof PersistenceException;
    }

    private boolean isAlreadySucceeded(String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(payment -> payment.getPaymentStatus() == PaymentStatus.SUCCESS)
                .orElse(false);
    }

    private boolean markPaymentExecuting(PaymentConfirmCommand command) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status ->
                paymentUpdateService.tryMarkConfirmExecuting(new PaymentStatusUpdateCommand(
                        command.paymentKey(),
                        command.orderId(),
                        PaymentStatus.EXECUTING,
                        null,
                        null
                ))
        ));
    }

    private void completePaymentSuccess(PaymentConfirmCommand command, PaymentExecutionResult result) {
        transactionTemplate.execute(status -> {
            paymentUpdateService.updateStatus(
                    new PaymentStatusUpdateCommand(
                            command.paymentKey(),
                            command.orderId(),
                            PaymentStatus.SUCCESS,
                            result.paymentExtraDetails(),
                            null
                    )
            );
            paymentOutboxService.savePaymentCompletedEvent(command, result);
            return null;
        });
    }

    private void recordPaymentLedger(PaymentConfirmCommand command, PaymentExecutionResult result) {
        Payment payment = paymentRepository.findByOrderId(command.orderId())
                .orElseThrow(() -> new ResourceException.NotFound("결제 정보 없음"));
        validatePaymentAmountMatchesPsp(payment, result);

        LedgerEntryRequest request = new LedgerEntryRequest(
                "PAYMENT:" + payment.getId() + ":COMPLETED",
                "PAYMENT",
                payment.getId(),
                payment.getOrderId(),
                payment.getBuyerId(),
                payment.getSellerId(),
                BigDecimal.valueOf(payment.getAmount()),
                "KRW",
                "CREDIT",
                "결제 완료"
        );

        LedgerEntryResponse response = ledgerClient.recordEntry(request);
        log.info(
                "Payment ledger entry recorded. orderId={}, paymentId={}, entryId={}",
                payment.getOrderId(),
                payment.getId(),
                response == null ? null : response.entryId()
        );
    }

    private void validatePaymentAmountMatchesPsp(Payment payment, PaymentExecutionResult result) {
        long persistedAmount = payment.getAmount().longValue();
        long pspAmount = result.paymentExtraDetails().totalAmount();

        if (persistedAmount != pspAmount) {
            throw PSPConfirmationException.builder()
                    .errorCode("PAYMENT_AMOUNT_MISMATCH")
                    .errorMessage("결제 금액이 저장된 Payment 금액과 일치하지 않습니다.")
                    .isSuccess(false)
                    .isFailure(false)
                    .isUnknown(true)
                    .isRetryable(true)
                    .build();
        }
    }

    private void markPaymentFailedOrUnknown(PaymentConfirmCommand command, PaymentStatus status, PaymentFailure failure) {
        if (status == PaymentStatus.UNKNOWN && isAlreadyRetryableFailureState(command.orderId())) {
            log.warn("Skip UNKNOWN status re-recording for retryable payment state. orderId={}, failure={}",
                    command.orderId(),
                    failure);
            return;
        }

        transactionTemplate.execute(transactionStatus -> {
            paymentUpdateService.updateStatus(
                    new PaymentStatusUpdateCommand(
                            command.paymentKey(),
                            command.orderId(),
                            status,
                            null,
                            failure
                    )
            );
            return null;
        });
    }

    private void markPaymentUnknownBestEffort(PaymentConfirmCommand command, PaymentFailure failure, Throwable originalFailure) {
        try {
            markPaymentFailedOrUnknown(command, PaymentStatus.UNKNOWN, failure);
        } catch (Exception recoveryFailure) {
            log.error(
                    "Payment UNKNOWN recovery failed. orderId={}, paymentKey={}, originalFailure={}, recoveryFailure={}",
                    command.orderId(),
                    command.paymentKey(),
                    originalFailure.getClass().getSimpleName(),
                    recoveryFailure.getClass().getSimpleName(),
                    recoveryFailure
            );
        }
    }

    private boolean isAlreadyRetryableFailureState(String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(payment -> payment.getPaymentStatus() == PaymentStatus.FAILURE ||
                        payment.getPaymentStatus() == PaymentStatus.UNKNOWN)
                .orElse(false);
    }

    private void completeBookingPayment(String orderId) {
        try {
            bookingCommandService.completePayment(orderId);
        } catch (Exception e) {
            log.error("Payment confirmed, but booking payment completion failed. orderId={}", orderId, e);
        }
    }

    private PaymentConfirmResult handleConfirmExecutionAlreadyClaimed(String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceException.NotFound("결제 정보 없음"));

        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
            completeBookingPayment(orderId);
            return new PaymentConfirmResult(PaymentStatus.SUCCESS, null);
        }

        return new PaymentConfirmResult(payment.getPaymentStatus(), null);
    }

    private void validateCheckoutReady(Booking booking) {
        if (booking.getStatus() != BookingStatus.PAYMENT_PENDING) {
            throw new BusinessException(ErrorCode.INVALID_SCHEDULE_STATUS);
        }
    }
}
