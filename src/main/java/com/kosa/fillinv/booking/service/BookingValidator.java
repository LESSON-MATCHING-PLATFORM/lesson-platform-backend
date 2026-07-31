package com.kosa.fillinv.booking.service;

import com.kosa.fillinv.category.entity.Category;
import com.kosa.fillinv.category.repository.CategoryRepository;
import com.kosa.fillinv.global.exception.BusinessException;
import com.kosa.fillinv.global.response.ErrorCode;
import com.kosa.fillinv.lesson.entity.AvailableTime;
import com.kosa.fillinv.lesson.entity.Lesson;
import com.kosa.fillinv.lesson.entity.Option;
import com.kosa.fillinv.lesson.repository.AvailableTimeRepository;
import com.kosa.fillinv.lesson.repository.LessonRepository;
import com.kosa.fillinv.lesson.repository.OptionRepository;
import com.kosa.fillinv.member.entity.Member;
import com.kosa.fillinv.member.repository.MemberRepository;
import com.kosa.fillinv.booking.entity.Booking;
import com.kosa.fillinv.booking.entity.BookingSession;
import com.kosa.fillinv.booking.repository.BookingRepository;
import com.kosa.fillinv.booking.repository.BookingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookingValidator {

    private final BookingRepository bookingRepository;
    private final LessonRepository lessonRepository;
    private final OptionRepository optionRepository;
    private final AvailableTimeRepository availableTimeRepository;
    private final CategoryRepository categoryRepository;
    private final MemberRepository memberRepository;
    private final BookingSessionRepository bookingSessionRepository;

    public Member getMentor(String mentorId) {
        return memberRepository.findById(mentorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MENTOR_NOT_FOUND));
    }

    public Lesson getLesson(String lessonId) {
        return lessonRepository.findById(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LESSON_NOT_FOUND));
    }

    public Option getOption(String optionId) {
        return optionRepository.findById(optionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OPTION_NOT_FOUND));
    }

    public AvailableTime getAvailableTime(String id) {
        return availableTimeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.AVAILABLE_TIME_NOT_FOUND));
    }

    public Category getCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    public Booking getBooking(String bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    public BookingSession getBookingSession(String bookingSessionId) {
        return bookingSessionRepository.findById(bookingSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_TIME_NOT_FOUND));
    }

    public String getNickname(String memberId) {
        return memberRepository.findById(memberId)
                .map(Member::getNickname)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
