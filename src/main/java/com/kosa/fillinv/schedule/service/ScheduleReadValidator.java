package com.kosa.fillinv.schedule.service;

import com.kosa.fillinv.booking.entity.Booking;
import com.kosa.fillinv.booking.entity.BookingSession;
import com.kosa.fillinv.booking.repository.BookingRepository;
import com.kosa.fillinv.booking.repository.BookingSessionRepository;
import com.kosa.fillinv.global.exception.BusinessException;
import com.kosa.fillinv.global.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ScheduleReadValidator {

    private final BookingRepository bookingRepository;
    private final BookingSessionRepository bookingSessionRepository;

    Booking getBooking(String bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    BookingSession getBookingSession(String bookingSessionId) {
        return bookingSessionRepository.findById(bookingSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_TIME_NOT_FOUND));
    }
}
