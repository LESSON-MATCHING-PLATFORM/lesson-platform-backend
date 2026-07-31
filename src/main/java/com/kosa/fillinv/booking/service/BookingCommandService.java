package com.kosa.fillinv.booking.service;

import com.kosa.fillinv.category.entity.Category;
import com.kosa.fillinv.global.exception.BusinessException;
import com.kosa.fillinv.global.response.ErrorCode;
import com.kosa.fillinv.lesson.entity.AvailableTime;
import com.kosa.fillinv.lesson.entity.Lesson;
import com.kosa.fillinv.lesson.entity.Option;
import com.kosa.fillinv.lesson.repository.AvailableTimeRepository;
import com.kosa.fillinv.member.entity.Member;
import com.kosa.fillinv.booking.dto.request.BookingCreateRequest;
import com.kosa.fillinv.booking.entity.BookingCancelReason;
import com.kosa.fillinv.booking.entity.Booking;
import com.kosa.fillinv.booking.entity.BookingSession;
import com.kosa.fillinv.booking.entity.BookingStatus;
import com.kosa.fillinv.booking.repository.BookingRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class BookingCommandService {

    private final BookingValidator validator;
    private final BookingRepository bookingRepository;
    private final AvailableTimeRepository availableTimeRepository;
    private final BookingStockService bookingStockService;

    public String createBooking(String memberId, BookingCreateRequest request) {
        Lesson lesson = validator.getLesson(request.lessonId());

        Booking booking = switch (lesson.getLessonType()) {
            case MENTORING -> createMentoringBooking(lesson, memberId, request);
            case ONEDAY -> createOnedayBooking(lesson, memberId, request);
            case STUDY -> createStudyBooking(lesson, memberId);
            default -> throw new BusinessException(ErrorCode.INVALID_LESSON_TYPE);
        };

        Booking saved = bookingRepository.save(booking);

        bookingStockService.reserve(saved);

        bookingRepository.save(booking);
        return booking.getId();
    }

    @Transactional
    public void completePayment(String bookingId) {
        Booking booking = validator.getBooking(bookingId);

        // 결제 대기 상태인 스케쥴만 승인 대기로 상태 변경 가능
        if (booking.getStatus() != BookingStatus.PAYMENT_PENDING) {
            throw new BusinessException(ErrorCode.INVALID_SCHEDULE_STATUS);
        }

        booking.updateStatus(BookingStatus.APPROVAL_PENDING);
    }

    // 멘토가 멘티의 레슨 수강신청을 승인했을 경우 (승인 대기 -> 승인)
    @Transactional
    public void approveLessonByMentor(String memberId, String bookingId) {
        Booking booking = validator.getBooking(bookingId);

        // 대기중인 스케줄 승인은 멘토만 가능
        booking.validateMentor(memberId);

        // 승인 대기 상태인 스케쥴만 승인으로 상태 변경 가능
        if (booking.getStatus() != BookingStatus.APPROVAL_PENDING) {
            throw new BusinessException(ErrorCode.INVALID_SCHEDULE_STATUS);
        }

        booking.updateStatus(BookingStatus.APPROVED);
    }


    // 멘토가 멘티의 레슨 수강신청을 거절했을 경우(승인 대기 -> 취소)
    @Transactional
    public void rejectLessonByMentor(String memberId, String bookingId) {
        Booking booking = validator.getBooking(bookingId);

        // 스케쥴 취소는 스케쥴 멘토만 가능
        booking.validateMentor(memberId);

        // 승인 대기 상태인 스케쥴만 취소로 상태 변경 가능
        if (booking.getStatus() != BookingStatus.APPROVAL_PENDING) {
            throw new BusinessException(ErrorCode.INVALID_SCHEDULE_STATUS);
        }

        cancelWithStockRestore(booking, BookingCancelReason.MENTOR_REJECTED);
    }

    @Transactional
    public void cancelByRefund(String bookingId) {
        Booking booking = validator.getBooking(bookingId);

        if (booking.getStatus() == BookingStatus.CANCELED) {
            return;
        }

        if (booking.getStatus() != BookingStatus.APPROVAL_PENDING &&
                booking.getStatus() != BookingStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_SCHEDULE_STATUS);
        }

        cancelWithStockRestore(booking, BookingCancelReason.REFUND_COMPLETED);
    }

    // 해당 레슨 수강이 모두 끝난 경우 (승인 -> 완료)
    @Transactional
    public void completeLesson(String memberId, String bookingId) {
        Booking booking = validator.getBooking(bookingId);

        // 스케쥴 완료는 멘티만 가능
        booking.validateMentee(memberId);

        // 승인 상태인 스케쥴만 완료로 상태 변경 가능
        if (booking.getStatus() != BookingStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_SCHEDULE_STATUS);
        }

        booking.updateStatus(BookingStatus.COMPLETED);
    }

    private void cancelWithStockRestore(Booking booking, BookingCancelReason reason) {
        boolean canceled = booking.cancel(reason);
        if (canceled) {
            bookingStockService.restore(booking);
        }
    }

    private Booking createMentoringBooking(
            Lesson lesson,
            String memberId,
            BookingCreateRequest request
    ) {
        Option option = validator.getOption(request.optionId());

        Booking booking = buildBaseBooking(lesson, memberId, option, null, option.getPrice());

        Instant startTime = request.startTime();
        Instant endTime = startTime.plus(option.getMinute(), ChronoUnit.MINUTES); // 옵션의 분 단위를 더해서 종료 시간 계산

        booking.addSession(
                BookingSession.of(startTime, endTime, booking)
        );

        return booking;
    }

    private Booking createOnedayBooking(
            Lesson lesson,
            String memberId,
            BookingCreateRequest request
    ) {
        AvailableTime availableTime = validator.getAvailableTime(request.availableTimeId());

        Booking booking = buildBaseBooking(lesson, memberId, null, availableTime, availableTime.getPrice());

        booking.addSession(
                BookingSession.of(
                        availableTime.getStartTime(),
                        availableTime.getEndTime(),
                        booking
                )
        );

        return booking;
    }

    private Booking createStudyBooking(
            Lesson lesson,
            String memberId
    ) {
        Booking booking = buildBaseBooking(lesson, memberId, null, null, lesson.getPrice());

        List<BookingSession> sessions = availableTimeRepository
                .findAllByLessonId(lesson.getId())
                .stream()
                .map(at -> BookingSession.of(at.getStartTime(), at.getEndTime(), booking))
                .toList();

        booking.addSessions(sessions);
        return booking;
    }
    public Booking buildBaseBooking(
            Lesson lesson,
            String memberId,
            Option option,
            AvailableTime availableTime,
            Integer price
    ) {
        Category category = validator.getCategory(lesson.getCategoryId());
        Member mentor = validator.getMentor(lesson.getMentorId());

        return Booking.builder()
                .id(UUID.randomUUID().toString())
                .mentorId(lesson.getMentorId())
                .menteeId(memberId)
                .mentorNickname(mentor.getNickname())
                .lessonId(lesson.getId())
                .lessonTitle(lesson.getTitle())
                .lessonType(lesson.getLessonType().name())
                .lessonDescription(lesson.getDescription())
                .lessonLocation(
                        lesson.getLocation() != null ? lesson.getLocation() : "장소 미정"
                )
                .lessonCategoryName(category.getName())
                .price(price)
                .optionId(option != null ? option.getId() : null)
                .optionName(option != null ? option.getName() : null)
                .optionMinute(option != null ? option.getMinute() : null)
                .availableTimeId(availableTime != null ? availableTime.getId() : null)
                .status(BookingStatus.PAYMENT_PENDING)
                .sessions(new ArrayList<>())
                .build();
    }
}
