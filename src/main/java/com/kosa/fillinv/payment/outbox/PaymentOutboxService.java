package com.kosa.fillinv.payment.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosa.fillinv.global.exception.ResourceException;
import com.kosa.fillinv.member.entity.Member;
import com.kosa.fillinv.member.repository.MemberRepository;
import com.kosa.fillinv.payment.domain.PaymentEvent;
import com.kosa.fillinv.payment.domain.PaymentExecutionResult;
import com.kosa.fillinv.payment.domain.RefundCompletedEvent;
import com.kosa.fillinv.payment.domain.RefundExecutionResult;
import com.kosa.fillinv.payment.entity.Payment;
import com.kosa.fillinv.payment.repository.PaymentRepository;
import com.kosa.fillinv.payment.service.dto.PaymentConfirmCommand;
import com.kosa.fillinv.payment.service.dto.PGCancelCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentOutboxService {

    static final String PAYMENT_COMPLETED = "PAYMENT_COMPLETED";
    static final String REFUND_COMPLETED = "REFUND_COMPLETED";
    static final String PAYMENT_TOPIC = "payment-topic";

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final PaymentRepository paymentRepository;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public PaymentOutboxEvent savePaymentCompletedEvent(PaymentConfirmCommand command, PaymentExecutionResult result) {
        Payment payment = paymentRepository.findByOrderId(command.orderId())
                .orElseThrow(() -> new ResourceException.NotFound("결제 정보 없음"));
        String recipientId = payment.getBuyerId();
        String recipientName = findRecipientName(recipientId);

        PaymentEvent event = new PaymentEvent(
                recipientId,
                recipientName,
                PAYMENT_COMPLETED,
                String.valueOf(command.amount()),
                command.orderId(),
                result.paymentExtraDetails().approvedAt().toString(),
                result.paymentExtraDetails().orderName()
        );

        PaymentOutboxEvent outboxEvent = PaymentOutboxEvent.builder()
                .id(UUID.randomUUID().toString())
                .eventType(PAYMENT_COMPLETED)
                .aggregateId(command.orderId())
                .topic(PAYMENT_TOPIC)
                .payload(toJson(event))
                .build();

        return paymentOutboxRepository.save(outboxEvent);
    }

    private String findRecipientName(String recipientId) {
        return memberRepository.findById(recipientId)
                .map(Member::getNickname)
                .orElseGet(() -> {
                    log.warn("Payment completed event recipient member not found. recipientId={}", recipientId);
                    return recipientId;
                });
    }

    @Transactional
    public PaymentOutboxEvent saveRefundCompletedEvent(PGCancelCommand command, RefundExecutionResult result) {
        RefundCompletedEvent event = new RefundCompletedEvent(
                REFUND_COMPLETED,
                command.refundId(),
                command.paymentKey(),
                command.orderId(),
                String.valueOf(result.refundExtraDetails().refundAmount()),
                result.refundExtraDetails().transactionKey(),
                result.refundExtraDetails().refundedAt().toString(),
                result.refundExtraDetails().refundReason()
        );

        PaymentOutboxEvent outboxEvent = PaymentOutboxEvent.builder()
                .id(UUID.randomUUID().toString())
                .eventType(REFUND_COMPLETED)
                .aggregateId(command.refundId())
                .topic(PAYMENT_TOPIC)
                .payload(toJson(event))
                .build();

        return paymentOutboxRepository.save(outboxEvent);
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Payment outbox event serialization failed", e);
        }
    }
}
