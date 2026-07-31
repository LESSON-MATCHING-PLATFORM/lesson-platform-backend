package com.kosa.fillinv.schedule.service.dto;

import com.kosa.fillinv.schedule.entity.ScheduleStatus;
import com.kosa.fillinv.schedule.repository.ScheduleParticipantRole;
import lombok.Builder;
import lombok.With;

import java.time.Instant;

@Builder(toBuilder = true)
public record ScheduleSearchCondition(
        @With String keyword,
        @With ScheduleStatus status,
        @With Instant from,
        @With Instant to,
        @With ScheduleSortType sortType,
        @With String memberId,
        @With ScheduleParticipantRole participantRole,
        @With Integer page,
        @With Integer size
) {

    public ScheduleSearchCondition {
        if (sortType == null) {
            sortType = ScheduleSortType.START_TIME_ASC;
        }
        if (page == null || page < 0) {
            page = 0;
        }
        if (size == null || size <= 0) {
            size = 10;
        }
    }

    public static ScheduleSearchCondition defaultCondition() {
        return ScheduleSearchCondition.builder().build();
    }

    public ScheduleSearchCondition toPast(Instant to) {
        return this.toBuilder()
                .to(to)
                .sortType(ScheduleSortType.START_TIME_DESC)
                .build();
    }

    public ScheduleSearchCondition toIntended(Instant from) {
        return this.toBuilder()
                .from(from)
                .sortType(ScheduleSortType.START_TIME_ASC)
                .build();
    }

    public ScheduleSearchCondition participate(String memberId) {
        return this.toBuilder()
                .memberId(memberId)
                .participantRole(ScheduleParticipantRole.BOTH)
                .build();
    }

    public ScheduleSearchCondition mentee(String memberId) {
        return this.toBuilder()
                .memberId(memberId)
                .participantRole(ScheduleParticipantRole.MENTEE)
                .build();
    }

    public ScheduleSearchCondition mentor(String memberId) {
        return this.toBuilder()
                .memberId(memberId)
                .participantRole(ScheduleParticipantRole.MENTOR)
                .build();
    }

    public ScheduleSearchCondition between(Instant start, Instant end) {
        return this.toBuilder()
                .from(start)
                .to(end)
                .build();
    }
}
