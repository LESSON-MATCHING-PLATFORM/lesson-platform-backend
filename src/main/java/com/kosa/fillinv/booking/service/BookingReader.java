package com.kosa.fillinv.booking.service;

import com.kosa.fillinv.booking.entity.Booking;
import com.kosa.fillinv.booking.exception.BookingException;
import com.kosa.fillinv.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class BookingReader {

    private final BookingRepository bookingRepository;

    Booking getBooking(String bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(BookingException.BookingNotFound::new);
    }

    Booking getBookingForUpdate(String bookingId) {
        return bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(BookingException.BookingNotFound::new);
    }
}
