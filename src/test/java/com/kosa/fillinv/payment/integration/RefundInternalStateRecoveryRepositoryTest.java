package com.kosa.fillinv.payment.integration;

import com.kosa.fillinv.lesson.entity.LessonType;
import com.kosa.fillinv.payment.entity.Refund;
import com.kosa.fillinv.payment.entity.RefundStatus;
import com.kosa.fillinv.payment.repository.RefundRepository;
import com.kosa.fillinv.booking.entity.Booking;
import com.kosa.fillinv.booking.entity.BookingStatus;
import com.kosa.fillinv.booking.repository.BookingRepository;
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
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never",
        "TOSS_SECRET_KEY=test-secret"
})
@Transactional
class RefundInternalStateRecoveryRepositoryTest {

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        refundRepository.deleteAll();
        bookingRepository.deleteAll();
    }

    @Test
    @DisplayName("Refund SUCCESS이고 Booking이 APPROVAL_PENDING 또는 APPROVED인 항목만 내부 상태 복구 대상으로 조회한다")
    void findRefundsPendingInternalStateRecovery() {
        bookingRepository.save(booking("booking-approval", BookingStatus.APPROVAL_PENDING));
        bookingRepository.save(booking("booking-approved", BookingStatus.APPROVED));
        bookingRepository.save(booking("booking-canceled", BookingStatus.CANCELED));
        bookingRepository.save(booking("booking-payment-pending", BookingStatus.PAYMENT_PENDING));

        refundRepository.save(successRefund("refund-approval", "booking-approval"));
        refundRepository.save(successRefund("refund-approved", "booking-approved"));
        refundRepository.save(successRefund("refund-canceled", "booking-canceled"));
        refundRepository.save(successRefund("refund-payment-pending", "booking-payment-pending"));
        refundRepository.save(failureRefund("refund-failure", "booking-approved"));

        entityManager.flush();
        entityManager.clear();

        List<Refund> refunds = refundRepository.findRefundsPendingInternalStateRecovery(
                RefundStatus.SUCCESS,
                List.of(BookingStatus.APPROVAL_PENDING, BookingStatus.APPROVED),
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

    private Booking booking(String bookingId, BookingStatus status) {
        return Booking.builder()
                .id(bookingId)
                .status(status)
                .requestContent("신청합니다")
                .lessonTitle("자바 레슨")
                .lessonType(LessonType.ONEDAY.name())
                .lessonDescription("자바를 공부합니다")
                .lessonLocation("온라인")
                .lessonCategoryName("개발")
                .mentorNickname("멘토")
                .price(1000)
                .lessonId("lesson-" + bookingId)
                .menteeId("mentee-001")
                .mentorId("mentor-001")
                .availableTimeId("available-time-" + bookingId)
                .build();
    }
}
