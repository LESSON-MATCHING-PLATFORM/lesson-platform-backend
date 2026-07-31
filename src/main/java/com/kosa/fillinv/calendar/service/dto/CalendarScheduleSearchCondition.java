package com.kosa.fillinv.calendar.service.dto;

import com.kosa.fillinv.schedule.entity.ScheduleStatus;
import com.kosa.fillinv.schedule.repository.ScheduleParticipantRole;
import lombok.Builder;
import lombok.With;

import java.time.Instant;

@Builder(toBuilder = true)
public record CalendarScheduleSearchCondition(
        @With String keyword,
        @With ScheduleStatus status,
        @With Instant from,
        @With Instant to,
        @With CalendarScheduleSortType sortType,
        @With String memberId,
        @With ScheduleParticipantRole participantRole,
        @With Integer page,
        @With Integer size
) {

    public CalendarScheduleSearchCondition {
        if (sortType == null) {
            sortType = CalendarScheduleSortType.START_TIME_ASC;
        }
        if (page == null || page < 0) {
            page = 0;
        }
        if (size == null || size <= 0) {
            size = 10;
        }
    }

    public static CalendarScheduleSearchCondition defaultCondition() {
        return CalendarScheduleSearchCondition.builder().build();
    }

    public CalendarScheduleSearchCondition toPast(Instant to) {
        return this.toBuilder()
                .to(to)
                .sortType(CalendarScheduleSortType.START_TIME_DESC)
                .build();
    }

    public CalendarScheduleSearchCondition toIntended(Instant from) {
        return this.toBuilder()
                .from(from)
                .sortType(CalendarScheduleSortType.START_TIME_ASC)
                .build();
    }

    public CalendarScheduleSearchCondition participate(String memberId) {
        return this.toBuilder()
                .memberId(memberId)
                .participantRole(ScheduleParticipantRole.BOTH)
                .build();
    }

    public CalendarScheduleSearchCondition mentee(String memberId) {
        return this.toBuilder()
                .memberId(memberId)
                .participantRole(ScheduleParticipantRole.MENTEE)
                .build();
    }

    public CalendarScheduleSearchCondition mentor(String memberId) {
        return this.toBuilder()
                .memberId(memberId)
                .participantRole(ScheduleParticipantRole.MENTOR)
                .build();
    }

    public CalendarScheduleSearchCondition between(Instant start, Instant end) {
        return this.toBuilder()
                .from(start)
                .to(end)
                .build();
    }
}
