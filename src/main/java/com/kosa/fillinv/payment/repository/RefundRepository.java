package com.kosa.fillinv.payment.repository;

import com.kosa.fillinv.payment.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RefundRepository extends JpaRepository<Refund, String> {

    @Query("select r.retryCount from Refund r where r.id = :refundId")
    Integer getRetryCountByRefundId(@Param("refundId") String refundId);
}
