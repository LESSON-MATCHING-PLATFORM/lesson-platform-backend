package com.kosa.fillinv.payment.integration;

import com.kosa.fillinv.lesson.entity.LessonType;
import com.kosa.fillinv.payment.entity.Refund;
import com.kosa.fillinv.payment.entity.RefundStatus;
import com.kosa.fillinv.payment.repository.RefundRepository;
import com.kosa.fillinv.schedule.entity.Schedule;
import com.kosa.fillinv.schedule.entity.ScheduleStatus;
import com.kosa.fillinv.schedule.repository.ScheduleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:refund-internal-recovery-repository-test;MODE=MySQL;NON_KEYWORDS=MINUTE;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "TOSS_SECRET_KEY=test-secret"
})
@Transactional
class RefundInternalStateRecoveryRepositoryTest {

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        refundRepository.deleteAll();
        scheduleRepository.deleteAll();
    }

    @Test
    @DisplayName("Refund SUCCESS이고 Schedule이 APPROVAL_PENDING 또는 APPROVED인 항목만 내부 상태 복구 대상으로 조회한다")
    void findRefundsPendingInternalStateRecovery() {
        scheduleRepository.save(schedule("schedule-approval", ScheduleStatus.APPROVAL_PENDING));
        scheduleRepository.save(schedule("schedule-approved", ScheduleStatus.APPROVED));
        scheduleRepository.save(schedule("schedule-canceled", ScheduleStatus.CANCELED));
        scheduleRepository.save(schedule("schedule-payment-pending", ScheduleStatus.PAYMENT_PENDING));

        refundRepository.save(successRefund("refund-approval", "schedule-approval"));
        refundRepository.save(successRefund("refund-approved", "schedule-approved"));
        refundRepository.save(successRefund("refund-canceled", "schedule-canceled"));
        refundRepository.save(successRefund("refund-payment-pending", "schedule-payment-pending"));
        refundRepository.save(failureRefund("refund-failure", "schedule-approved"));

        entityManager.flush();
        entityManager.clear();

        List<Refund> refunds = refundRepository.findRefundsPendingInternalStateRecovery(
                RefundStatus.SUCCESS,
                List.of(ScheduleStatus.APPROVAL_PENDING, ScheduleStatus.APPROVED),
                PageRequest.of(0, 100)
        );

        assertThat(refunds)
                .extracting(Refund::getId)
                .containsExactly("refund-approval", "refund-approved");
    }

    private Refund successRefund(String refundId, String orderId) {
        Refund refund = refund(refundId, orderId);
        refund.markExecuting(Instant.parse("2026-07-31T00:00:00Z"));
        refund.markSuccess("transaction-" + refundId, Instant.parse("2026-07-31T00:00:01Z"), "raw");
        return refund;
    }

    private Refund failureRefund(String refundId, String orderId) {
        Refund refund = refund(refundId, orderId);
        refund.markExecuting(Instant.parse("2026-07-31T00:00:00Z"));
        refund.markFail(Instant.parse("2026-07-31T00:01:00Z"));
        return refund;
    }

    private Refund refund(String refundId, String orderId) {
        return Refund.builder()
                .id(refundId)
                .paymentId("payment-" + refundId)
                .paymentKey("payment-key-" + refundId)
                .orderId(orderId)
                .refundStatus(RefundStatus.NOT_STARTED)
                .refundAmount(1000)
                .refundReason("단순 변심")
                .build();
    }

    private Schedule schedule(String scheduleId, ScheduleStatus status) {
        return Schedule.builder()
                .id(scheduleId)
                .status(status)
                .requestContent("신청합니다")
                .lessonTitle("자바 레슨")
                .lessonType(LessonType.ONEDAY.name())
                .lessonDescription("자바를 공부합니다")
                .lessonLocation("온라인")
                .lessonCategoryName("개발")
                .mentorNickname("멘토")
                .price(1000)
                .lessonId("lesson-" + scheduleId)
                .menteeId("mentee-001")
                .mentorId("mentor-001")
                .availableTimeId("available-time-" + scheduleId)
                .build();
    }
}
