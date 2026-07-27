package com.kosa.fillinv.payment.repository;

import com.kosa.fillinv.payment.entity.Refund;
import com.kosa.fillinv.payment.entity.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface RefundRepository extends JpaRepository<Refund, String> {

    boolean existsByPaymentId(String paymentId);

    @Query("select r.retryCount from Refund r where r.id = :refundId")
    Integer getRetryCountByRefundId(@Param("refundId") String refundId);

    List<Refund> findTop100ByRefundStatusInAndRetryCountLessThanAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            Collection<RefundStatus> refundStatuses,
            Integer retryCount,
            Instant now
    );
}
