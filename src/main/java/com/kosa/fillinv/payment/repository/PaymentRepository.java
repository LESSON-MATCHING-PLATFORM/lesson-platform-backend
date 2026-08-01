package com.kosa.fillinv.payment.repository;

import com.kosa.fillinv.payment.entity.Payment;
import com.kosa.fillinv.payment.entity.PaymentStatus;
import com.kosa.fillinv.booking.entity.BookingStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {
    Optional<Payment> findByOrderId(String s);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Payment p
            SET p.paymentStatus = com.kosa.fillinv.payment.entity.PaymentStatus.EXECUTING,
                p.paymentKey = :paymentKey
            WHERE p.orderId = :orderId
            AND p.paymentStatus = :currentStatus
            """)
    int markExecutingIfStatus(
            @Param("orderId") String orderId,
            @Param("paymentKey") String paymentKey,
            @Param("currentStatus") PaymentStatus currentStatus
    );

    @Query("""
            SELECT p
            FROM Payment p
            JOIN Booking b ON b.id = p.orderId
            WHERE p.paymentStatus = :paymentStatus
            AND b.status = :bookingStatus
            ORDER BY p.approvedAt ASC, p.createdAt ASC
            """)
    List<Payment> findByPaymentStatusAndBookingStatus(
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("bookingStatus") BookingStatus bookingStatus,
            Pageable pageable
    );
}
