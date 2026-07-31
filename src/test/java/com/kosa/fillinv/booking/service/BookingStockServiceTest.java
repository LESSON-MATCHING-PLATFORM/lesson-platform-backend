package com.kosa.fillinv.booking.service;

import com.kosa.fillinv.lesson.entity.LessonType;
import com.kosa.fillinv.booking.entity.Booking;
import com.kosa.fillinv.booking.entity.BookingStatus;
import com.kosa.fillinv.booking.exception.BookingException;
import com.kosa.fillinv.stock.repository.StockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingStockServiceTest {

    @Mock
    private StockRepository stockRepository;

    @InjectMocks
    private BookingStockService bookingStockService;

    @Test
    @DisplayName("ONEDAY Booking 생성 시 availableTimeId 기준으로 재고를 차감한다")
    void reserve_oneday_decreasesStockByAvailableTimeId() {
        Booking booking = booking(LessonType.ONEDAY, "lesson-001", "available-time-001");
        given(stockRepository.decreaseQuantity("available-time-001")).willReturn(1);

        bookingStockService.reserve(booking);

        verify(stockRepository).decreaseQuantity("available-time-001");
    }

    @Test
    @DisplayName("STUDY Booking 생성 시 lessonId 기준으로 재고를 차감한다")
    void reserve_study_decreasesStockByLessonId() {
        Booking booking = booking(LessonType.STUDY, "lesson-001", null);
        given(stockRepository.decreaseQuantity("lesson-001")).willReturn(1);

        bookingStockService.reserve(booking);

        verify(stockRepository).decreaseQuantity("lesson-001");
    }

    @Test
    @DisplayName("MENTORING Booking 생성 시 재고를 차감하지 않는다")
    void reserve_mentoring_doesNothing() {
        Booking booking = booking(LessonType.MENTORING, "lesson-001", null);

        bookingStockService.reserve(booking);

        verify(stockRepository, never()).decreaseQuantity(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("재고 차감 실패 시 BookingException이 발생한다")
    void reserve_whenNoSeat_throwsException() {
        Booking booking = booking(LessonType.STUDY, "lesson-001", null);
        given(stockRepository.decreaseQuantity("lesson-001")).willReturn(0);

        assertThatThrownBy(() -> bookingStockService.reserve(booking))
                .isInstanceOf(BookingException.class);
    }

    @Test
    @DisplayName("ONEDAY Booking 취소 시 availableTimeId 기준으로 재고를 복구한다")
    void restore_oneday_increasesStockByAvailableTimeId() {
        Booking booking = booking(LessonType.ONEDAY, "lesson-001", "available-time-001");

        bookingStockService.restore(booking);

        verify(stockRepository).increaseQuantity("available-time-001");
    }

    @Test
    @DisplayName("STUDY Booking 취소 시 lessonId 기준으로 재고를 복구한다")
    void restore_study_increasesStockByLessonId() {
        Booking booking = booking(LessonType.STUDY, "lesson-001", null);

        bookingStockService.restore(booking);

        verify(stockRepository).increaseQuantity("lesson-001");
    }

    @Test
    @DisplayName("MENTORING Booking 취소 시 재고를 복구하지 않는다")
    void restore_mentoring_doesNothing() {
        Booking booking = booking(LessonType.MENTORING, "lesson-001", null);

        bookingStockService.restore(booking);

        verify(stockRepository, never()).increaseQuantity(org.mockito.ArgumentMatchers.anyString());
    }

    private Booking booking(LessonType lessonType, String lessonId, String availableTimeId) {
        return Booking.builder()
                .id("schedule-001")
                .status(BookingStatus.APPROVAL_PENDING)
                .requestContent("신청합니다")
                .lessonTitle("자바 스터디")
                .lessonType(lessonType.name())
                .lessonDescription("자바를 공부합니다")
                .lessonLocation("온라인")
                .lessonCategoryName("개발")
                .mentorNickname("멘토")
                .price(10000)
                .lessonId(lessonId)
                .menteeId("mentee-001")
                .mentorId("mentor-001")
                .availableTimeId(availableTimeId)
                .build();
    }
}
