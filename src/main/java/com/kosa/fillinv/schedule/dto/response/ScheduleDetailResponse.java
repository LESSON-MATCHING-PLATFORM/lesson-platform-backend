package com.kosa.fillinv.schedule.dto.response;

import com.kosa.fillinv.booking.entity.Booking;
import com.kosa.fillinv.booking.entity.BookingSession;
import com.kosa.fillinv.booking.entity.BookingStatus;

import java.time.Instant;

public record ScheduleDetailResponse( // 스케쥴 상세 조회 (생성)
        String scheduleId,
        String lessonTitle,
        String category,
        String mentorNickname,
        String menteeNickname,
        Instant startTime,
        Instant endTime,
        String location,
        String description,
        String lessonType,
        String requestContent,
        BookingStatus status,
        Integer price,
        String optionName,
        String userRole
) {
    public static ScheduleDetailResponse from(
            Booking s,
            String mentorNickname,
            String menteeNickname,
            BookingSession session,
            String userRole) {
        return new ScheduleDetailResponse(
                s.getId(),
                s.getLessonTitle(),
                s.getLessonCategoryName(),
                mentorNickname,
                menteeNickname,
                session.getStartTime(),
                session.getEndTime(),
                s.getLessonLocation(),
                s.getLessonDescription(),
                s.getLessonType(),
                s.getRequestContent(),
                s.getStatus(),
                s.getPrice(),
                s.getOptionName(),
                userRole
        );
    }

}
