package com.kosa.fillinv.booking.service;

import com.kosa.fillinv.booking.entity.Booking;
import com.kosa.fillinv.booking.repository.BookingRepository;
import com.kosa.fillinv.global.exception.BusinessException;
import com.kosa.fillinv.global.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class BookingReader {

    private final BookingRepository bookingRepository;

    Booking getBooking(String bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
    }
}
