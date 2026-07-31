package com.kosa.fillinv.schedule.service;

import com.kosa.fillinv.category.entity.Category;
import com.kosa.fillinv.global.exception.BusinessException;
import com.kosa.fillinv.global.response.ErrorCode;
import com.kosa.fillinv.lesson.entity.AvailableTime;
import com.kosa.fillinv.lesson.entity.Lesson;
import com.kosa.fillinv.lesson.entity.LessonType;
import com.kosa.fillinv.lesson.entity.Option;
import com.kosa.fillinv.lesson.repository.AvailableTimeRepository;
import com.kosa.fillinv.member.entity.Member;
import com.kosa.fillinv.schedule.dto.request.BookingCreateRequest;
import com.kosa.fillinv.schedule.entity.BookingCancelReason;
import com.kosa.fillinv.schedule.entity.Schedule;
import com.kosa.fillinv.schedule.entity.ScheduleStatus;
import com.kosa.fillinv.schedule.entity.ScheduleTime;
import com.kosa.fillinv.schedule.repository.ScheduleRepository;
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
    private final ScheduleRepository scheduleRepository;
    private final AvailableTimeRepository availableTimeRepository;
    private final BookingStockService bookingStockService;

    public String createBooking(String memberId, BookingCreateRequest request) {
        Lesson lesson = validator.getLesson(request.lessonId());

        Schedule schedule = switch (lesson.getLessonType()) {
            case MENTORING -> createMentoringBooking(lesson, memberId, request);
            case ONEDAY -> createOnedayBooking(lesson, memberId, request);
            case STUDY -> createStudyBooking(lesson, memberId);
            default -> throw new BusinessException(ErrorCode.INVALID_LESSON_TYPE);
        };

        Schedule saved = scheduleRepository.save(schedule);

        bookingStockService.reserve(saved);

        scheduleRepository.save(schedule);
        return schedule.getId();
    }

    @Transactional
    public void completePayment(String scheduleId) {
        Schedule schedule = validator.getSchedule(scheduleId);

        // 결제 대기 상태인 스케쥴만 승인 대기로 상태 변경 가능
        if (schedule.getStatus() != ScheduleStatus.PAYMENT_PENDING) {
            throw new BusinessException(ErrorCode.INVALID_SCHEDULE_STATUS);
        }

        schedule.updateStatus(ScheduleStatus.APPROVAL_PENDING);
    }

    // 멘토가 멘티의 레슨 수강신청을 승인했을 경우 (승인 대기 -> 승인)
    @Transactional
    public void approveLessonByMentor(String memberId, String scheduleId) {
        Schedule schedule = validator.getSchedule(scheduleId);

        // 대기중인 스케줄 승인은 멘토만 가능
        schedule.validateMentor(memberId);

        // 승인 대기 상태인 스케쥴만 승인으로 상태 변경 가능
        if (schedule.getStatus() != ScheduleStatus.APPROVAL_PENDING) {
            throw new BusinessException(ErrorCode.INVALID_SCHEDULE_STATUS);
        }

        schedule.updateStatus(ScheduleStatus.APPROVED);
    }


    // 멘토가 멘티의 레슨 수강신청을 거절했을 경우(승인 대기 -> 취소)
    @Transactional
    public void rejectLessonByMentor(String memberId, String scheduleId) {
        Schedule schedule = validator.getSchedule(scheduleId);

        // 스케쥴 취소는 스케쥴 멘토만 가능
        schedule.validateMentor(memberId);

        // 승인 대기 상태인 스케쥴만 취소로 상태 변경 가능
        if (schedule.getStatus() != ScheduleStatus.APPROVAL_PENDING) {
            throw new BusinessException(ErrorCode.INVALID_SCHEDULE_STATUS);
        }

        cancelWithStockRestore(schedule, BookingCancelReason.MENTOR_REJECTED);
    }

    @Transactional
    public void cancelByRefund(String scheduleId) {
        Schedule schedule = validator.getSchedule(scheduleId);

        if (schedule.getStatus() == ScheduleStatus.CANCELED) {
            return;
        }

        if (schedule.getStatus() != ScheduleStatus.APPROVAL_PENDING &&
                schedule.getStatus() != ScheduleStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_SCHEDULE_STATUS);
        }

        cancelWithStockRestore(schedule, BookingCancelReason.REFUND_COMPLETED);
    }

    // 해당 레슨 수강이 모두 끝난 경우 (승인 -> 완료)
    @Transactional
    public void completeLesson(String memberId, String scheduleId) {
        Schedule schedule = validator.getSchedule(scheduleId);

        // 스케쥴 완료는 멘티만 가능
        schedule.validateMentee(memberId);

        // 승인 상태인 스케쥴만 완료로 상태 변경 가능
        if (schedule.getStatus() != ScheduleStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_SCHEDULE_STATUS);
        }

        schedule.updateStatus(ScheduleStatus.COMPLETED);
    }

    private void cancelWithStockRestore(Schedule schedule, BookingCancelReason reason) {
        boolean canceled = schedule.cancel(reason);
        if (canceled) {
            bookingStockService.restore(schedule);
        }
    }

    private Schedule createMentoringBooking(
            Lesson lesson,
            String memberId,
            BookingCreateRequest request
    ) {
        Option option = validator.getOption(request.optionId());

        Schedule schedule = buildBaseBooking(lesson, memberId, option, null, option.getPrice());

        Instant startTime = request.startTime();
        Instant endTime = startTime.plus(option.getMinute(), ChronoUnit.MINUTES); // 옵션의 분 단위를 더해서 종료 시간 계산

        schedule.addScheduleTime(
                ScheduleTime.of(startTime, endTime, schedule)
        );

        return schedule;
    }

    private Schedule createOnedayBooking(
            Lesson lesson,
            String memberId,
            BookingCreateRequest request
    ) {
        AvailableTime availableTime = validator.getAvailableTime(request.availableTimeId());

        Schedule schedule = buildBaseBooking(lesson, memberId, null, availableTime, availableTime.getPrice());

        schedule.addScheduleTime(
                ScheduleTime.of(
                        availableTime.getStartTime(),
                        availableTime.getEndTime(),
                        schedule
                )
        );

        return schedule;
    }

    private Schedule createStudyBooking(
            Lesson lesson,
            String memberId
    ) {
        Schedule schedule = buildBaseBooking(lesson, memberId, null, null, lesson.getPrice());

        List<ScheduleTime> times = availableTimeRepository
                .findAllByLessonId(lesson.getId())
                .stream()
                .map(at -> ScheduleTime.of(at.getStartTime(), at.getEndTime(), schedule))
                .toList();

        schedule.addScheduleTime(times);
        return schedule;
    }
    public Schedule buildBaseBooking(
            Lesson lesson,
            String memberId,
            Option option,
            AvailableTime availableTime,
            Integer price
    ) {
        Category category = validator.getCategory(lesson.getCategoryId());
        Member mentor = validator.getMentor(lesson.getMentorId());

        return Schedule.builder()
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
                .status(ScheduleStatus.PAYMENT_PENDING)
                .scheduleTimeList(new ArrayList<>())
                .build();
    }
}
