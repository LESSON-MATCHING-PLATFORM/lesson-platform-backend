package com.kosa.fillinv.payment.service;

import com.kosa.fillinv.global.exception.ResourceException;
import com.kosa.fillinv.payment.entity.Payment;
import com.kosa.fillinv.payment.entity.PaymentStatus;
import com.kosa.fillinv.payment.entity.Refund;
import com.kosa.fillinv.payment.entity.RefundStatus;
import com.kosa.fillinv.payment.repository.PaymentRepository;
import com.kosa.fillinv.payment.repository.RefundRepository;
import com.kosa.fillinv.payment.service.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;

    @Transactional
    public RefundDTO refund(PaymentRefundCommand command) {
        Payment payment = getPayment(command.paymentId());
        validateRefundRequest(payment, command);

        Refund refund = refundRepository.save(createRefund(payment, command));

        applicationEventPublisher.publishEvent(refund);

        return RefundDTO.of(refund);
    }

    private Payment getPayment(String paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceException.NotFound("결제 정보가 존재하지 않습니다."));
    }

    private void validateRefundRequest(Payment payment, PaymentRefundCommand command) {
        if (payment.getPaymentStatus() != PaymentStatus.SUCCESS) {
            throw new ResourceException.InvalidArgument("성공한 결제만 환불할 수 있습니다.");
        }

        if (command.cancelReason() == null || command.cancelReason().isBlank()) {
            throw new ResourceException.InvalidArgument("환불 사유는 필수입니다.");
        }

        if (command.refundAmount() == null || command.refundAmount() <= 0) {
            throw new ResourceException.InvalidArgument("환불 금액은 0보다 커야 합니다.");
        }

        if (command.refundAmount() > payment.getAmount()) {
            throw new ResourceException.InvalidArgument("환불 금액은 결제 금액보다 클 수 없습니다.");
        }

        if (refundRepository.existsByPaymentId(payment.getId())) {
            throw new ResourceException.InvalidArgument("이미 환불 요청이 존재합니다.");
        }
    }

    private Refund createRefund(Payment payment, PaymentRefundCommand command) {
        Refund newRefund = Refund.builder()
                .id(UUID.randomUUID().toString())
                .paymentId(command.paymentId())
                .paymentKey(payment.getPaymentKey())
                .orderId(payment.getOrderId())
                .refundAmount(command.refundAmount())
                .refundReason(command.cancelReason())
                .refundStatus(RefundStatus.NOT_STARTED)
                .build();
        return newRefund;
    }
}
