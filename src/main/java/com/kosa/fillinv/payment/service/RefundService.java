package com.kosa.fillinv.payment.service;

import com.kosa.fillinv.global.exception.ResourceException;
import com.kosa.fillinv.payment.entity.Payment;
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
        Refund refund = refundRepository.save(createRefund(command));

        applicationEventPublisher.publishEvent(refund);

        return RefundDTO.of(refund);
    }

    private Refund createRefund(PaymentRefundCommand command) {
        Payment payment = paymentRepository.findById(command.paymentId())
                .orElseThrow(() -> new ResourceException.NotFound("결제 정보가 존재하지 않습니다."));

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
