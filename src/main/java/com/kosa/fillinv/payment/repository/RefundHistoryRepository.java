package com.kosa.fillinv.payment.repository;

import com.kosa.fillinv.payment.entity.RefundHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RefundHistoryRepository extends JpaRepository<RefundHistory, String> {
    List<RefundHistory> findByPaymentKey(String paymentKey);
}
