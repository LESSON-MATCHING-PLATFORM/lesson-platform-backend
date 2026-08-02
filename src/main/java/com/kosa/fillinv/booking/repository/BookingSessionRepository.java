package com.kosa.fillinv.booking.repository;

import com.kosa.fillinv.booking.entity.BookingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingSessionRepository extends JpaRepository<BookingSession, String>, JpaSpecificationExecutor<BookingSession> {
}
