package com.kosa.fillinv.booking.service;

import com.kosa.fillinv.global.response.ErrorCode;
import com.kosa.fillinv.lesson.entity.LessonType;
import com.kosa.fillinv.booking.entity.Booking;
import com.kosa.fillinv.booking.exception.BookingException;
import com.kosa.fillinv.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingStockService {

    private final StockRepository stockRepository;

    public void reserve(Booking booking) {
        stockKey(booking).ifPresent(this::decrease);
    }

    public void restore(Booking booking) {
        stockKey(booking).ifPresent(key -> {
            if (stockRepository.increaseQuantity(key) == 0) {
                log.warn("Booking stock restore skipped because stock row was not found. bookingId={}, lessonType={}, stockKey={}",
                        booking.getId(),
                        booking.getLessonType(),
                        key);
            }
        });
    }

    private Optional<String> stockKey(Booking booking) {
        LessonType type = LessonType.from(booking.getLessonType());

        return switch (type) {
            case MENTORING -> Optional.empty();
            case ONEDAY -> Optional.of(booking.getAvailableTimeId());
            case STUDY -> Optional.of(booking.getLessonId());
        };
    }

    private void decrease(String key) {
        if (stockRepository.decreaseQuantity(key) == 0) {
            throw new BookingException(ErrorCode.NO_SEAT);
        }
    }
}
