package com.kosa.fillinv.payment.service;

import com.kosa.fillinv.global.exception.ResourceException;
import com.kosa.fillinv.payment.entity.Payment;
import com.kosa.fillinv.payment.entity.PaymentHistory;
import com.kosa.fillinv.payment.entity.PaymentStatus;
import com.kosa.fillinv.payment.repository.PaymentHistoryRepository;
import com.kosa.fillinv.payment.repository.PaymentRepository;
import com.kosa.fillinv.payment.service.dto.PaymentStatusUpdateCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentUpdateService {

    private final PaymentRepository paymentRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;

    private static PaymentHistory createPaymentHistory(Payment payment, PaymentStatus previousStatus, PaymentStatus newStatus, String reason) {
        return PaymentHistory.builder()
                .id(UUID.randomUUID().toString())
                .paymentId(payment.getId())
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .reason(reason)
                .build();
    }

    @Transactional
    public void execute(PaymentStatusUpdateCommand command) {
        Payment payment = paymentRepository.findByOrderId(command.orderId())
                .orElseThrow(() -> new ResourceException.NotFound("결제 정보 없음"));

        PaymentStatus previousStatus = payment.getPaymentStatus();

        payment.markExecuting();
        payment.setPaymentKey(command.paymentKey());

        PaymentHistory paymentHistory = createPaymentHistory(payment, previousStatus, PaymentStatus.EXECUTING, "PAYMENT_CONFIRMATION_START");
        paymentHistoryRepository.save(paymentHistory);
    }

    @Transactional
    public boolean tryMarkConfirmExecuting(PaymentStatusUpdateCommand command) {
        Payment payment = paymentRepository.findByOrderId(command.orderId())
                .orElseThrow(() -> new ResourceException.NotFound("결제 정보 없음"));
        PaymentStatus currentStatus = payment.getPaymentStatus();

        if (!isConfirmRetryable(currentStatus)) {
            return false;
        }

        if (paymentRepository.markExecutingIfStatus(
                command.orderId(),
                command.paymentKey(),
                currentStatus,
                PaymentStatus.EXECUTING
        ) == 1) {
            saveConfirmStartHistory(payment, currentStatus);
            return true;
        }

        return false;
    }

    @Transactional
    public void success(PaymentStatusUpdateCommand command) {
        Payment payment = paymentRepository.findByOrderId(command.orderId())
                .orElseThrow(() -> new ResourceException.NotFound("결제 정보 없음"));

        PaymentStatus previousStatus = payment.getPaymentStatus();

        payment.markSuccess();

        payment.setApprovedAt(command.extraDetails().approvedAt());
        payment.setPaymentMethod(command.extraDetails().method());
        payment.setPspRaw(command.extraDetails().pspRawData());

        PaymentHistory paymentHistory = createPaymentHistory(payment, previousStatus, PaymentStatus.SUCCESS, "PAYMENT_CONFIRM_DONE");
        paymentHistoryRepository.save(paymentHistory);
    }

    @Transactional
    public void fail(PaymentStatusUpdateCommand command) {
        Payment payment = paymentRepository.findByOrderId(command.orderId())
                .orElseThrow(() -> new ResourceException.NotFound("결제 정보 없음"));

        PaymentStatus previousStatus = payment.getPaymentStatus();

        payment.markFail();

        PaymentHistory paymentHistory = createPaymentHistory(payment, previousStatus, PaymentStatus.FAILURE, command.failure()==null? null : command.failure().toString());
        paymentHistoryRepository.save(paymentHistory);
    }

    @Transactional
    public void updateStatus(PaymentStatusUpdateCommand command) {
        switch (command.status()) {
            case EXECUTING -> execute(command);
            case SUCCESS -> success(command);
            case FAILURE -> fail(command);
            case UNKNOWN -> unknown(command);
            default -> throw new IllegalArgumentException("Unknown status: " + command.status());
        }
    }

    private void unknown(PaymentStatusUpdateCommand command) {
        Payment payment = paymentRepository.findByOrderId(command.orderId())
                .orElseThrow(() -> new ResourceException.NotFound("결제 정보 없음"));

        PaymentStatus previousStatus = payment.getPaymentStatus();

        payment.markUnknown();

        PaymentHistory paymentHistory = createPaymentHistory(payment, previousStatus, PaymentStatus.UNKNOWN, command.failure()==null? null : command.failure().toString());
        paymentHistoryRepository.save(paymentHistory);
    }

    private boolean isConfirmRetryable(PaymentStatus status) {
        return List.of(
                PaymentStatus.NOT_STARTED,
                PaymentStatus.FAILURE,
                PaymentStatus.UNKNOWN
        ).contains(status);
    }

    private void saveConfirmStartHistory(Payment payment, PaymentStatus previousStatus) {
        PaymentHistory paymentHistory = createPaymentHistory(
                payment,
                previousStatus,
                PaymentStatus.EXECUTING,
                "PAYMENT_CONFIRMATION_START"
        );
        paymentHistoryRepository.save(paymentHistory);
    }
}
