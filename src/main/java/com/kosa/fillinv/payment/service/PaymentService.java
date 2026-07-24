package com.kosa.fillinv.payment.service;

import com.kosa.fillinv.global.exception.ResourceException;
import com.kosa.fillinv.payment.client.TossPaymentClient;
import com.kosa.fillinv.payment.controller.dto.CheckoutCommand;
import com.kosa.fillinv.payment.controller.dto.CheckoutResult;
import com.kosa.fillinv.payment.domain.PSPConfirmationException;
import com.kosa.fillinv.payment.domain.PaymentExecutionResult;
import com.kosa.fillinv.payment.domain.PaymentFailure;
import com.kosa.fillinv.payment.entity.Payment;
import com.kosa.fillinv.payment.entity.PaymentStatus;
import com.kosa.fillinv.payment.repository.PaymentRepository;
import com.kosa.fillinv.payment.service.dto.PaymentConfirmCommand;
import com.kosa.fillinv.payment.service.dto.PaymentConfirmResult;
import com.kosa.fillinv.payment.service.dto.PaymentStatusUpdateCommand;
import com.kosa.fillinv.schedule.entity.Schedule;
import com.kosa.fillinv.schedule.repository.ScheduleRepository;
import com.kosa.fillinv.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;

import java.sql.SQLException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentUpdateService paymentUpdateService;
    private final TossPaymentClient tossPaymentClient;
    private final PaymentRepository paymentRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleService scheduleService;

    /*
     * 스케쥴에 대한 Payment 객체를 생성 및 데이터베이스에 저장
     * Payment 객체를 통해서 이후 결제 과정에서 상태를 관리
     * */
    @Transactional
    public CheckoutResult checkout(CheckoutCommand command) {

        String scheduleId = command.scheduleId();

        // 결제할 스케쥴 정보 조회
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceException.NotFound("스케쥴을 찾을 수 없습니다. scheduleId: " + scheduleId));

        Integer amount = schedule.getPrice();
        String orderName = schedule.getLessonTitle() + (schedule.getOptionName() != null ? " - " + schedule.getOptionName() : "");

        // 결제 준비
        Payment initPayment = Payment.builder()
                .id(UUID.randomUUID().toString())
                .orderId(scheduleId)
                .orderName(orderName)
                .buyerId(schedule.getMenteeId())
                .sellerId(schedule.getMentorId())
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
            // 결제 상태 진행 중으로 변경
            paymentUpdateService.updateStatus(new PaymentStatusUpdateCommand(
                    command.paymentKey(),
                    command.orderId(),
                    PaymentStatus.EXECUTING,
                    null,
                    null
            ));

            // 외부 결제사에게 승인 요청
            PaymentExecutionResult result = tossPaymentClient.confirm(command);

            // 결제 상태 성공으로 변경
            paymentUpdateService.updateStatus(
                    new PaymentStatusUpdateCommand(
                            command.paymentKey(),
                            command.orderId(),
                            PaymentStatus.SUCCESS,
                            result.paymentExtraDetails(),
                            null
                    )
            );

            // Todo TOSS confirm api는 성공하고 결제 상태 변경은 성공했으나 ScheduleStatus 변경에 실패한 경우 별도 처리 필요
            scheduleService.completePayment(command.orderId());

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
        } else if (e instanceof SQLException) { // Todo TOSS confirm api는 성공하고 내부 서버에서 상태 저장에 실패한 경우 (PaymentStatus.EXECUTING) 별도 처리 필요
            status = PaymentStatus.UNKNOWN;
            failure = new PaymentFailure(e.getClass().getSimpleName(), e.getMessage() == null ? "" : e.getMessage());
        } else if (e instanceof ResourceAccessException) { // time out or network
            status = PaymentStatus.UNKNOWN;
            failure = new PaymentFailure(e.getClass().getSimpleName(), e.getMessage() == null ? "" : e.getMessage());
        } else {
            status = PaymentStatus.FAILURE;
            failure = new PaymentFailure(e.getClass().getSimpleName(), e.getMessage() == null ? "" : e.getMessage());
        }

        paymentUpdateService.updateStatus(
                new PaymentStatusUpdateCommand(
                        command.paymentKey(),
                        command.orderId(),
                        status,
                        null,
                        failure
                )
        );

        return new PaymentConfirmResult(status, failure);
    }
}
