package com.kosa.fillinv.calendar.service;

import com.kosa.fillinv.calendar.repository.CalendarScheduleSpecifications;
import com.kosa.fillinv.calendar.service.dto.CalendarScheduleSearchCondition;
import com.kosa.fillinv.calendar.service.dto.CalendarScheduleSortType;
import com.kosa.fillinv.global.exception.BusinessException;
import com.kosa.fillinv.global.response.ErrorCode;
import com.kosa.fillinv.member.dto.profile.ProfileResponseDto;
import com.kosa.fillinv.member.service.MemberService;
import com.kosa.fillinv.schedule.dto.response.ScheduleDetailResponse;
import com.kosa.fillinv.schedule.dto.response.ScheduleListResponse;
import com.kosa.fillinv.schedule.entity.Schedule;
import com.kosa.fillinv.schedule.entity.ScheduleTime;
import com.kosa.fillinv.schedule.repository.ScheduleParticipantRole;
import com.kosa.fillinv.schedule.repository.ScheduleTimeRepository;
import com.kosa.fillinv.schedule.service.ScheduleValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class CalendarScheduleReadService {

    private final ScheduleTimeRepository scheduleTimeRepository;
    private final MemberService memberService;
    private final ScheduleValidator validator;

    public ScheduleDetailResponse getScheduleDetail(String memberId, String bookingId, String scheduleTimeId) {
        Schedule booking = validator.getSchedule(bookingId);
        ScheduleTime scheduleTime = validator.getScheduleTime(scheduleTimeId);

        if (!scheduleTime.getSchedule().getId().equals(bookingId)) {
            throw new BusinessException(ErrorCode.SCHEDULE_TIME_MISMATCH);
        }

        String mentorNickname = booking.getMentorNickname();
        String menteeNickname = validator.getNickname(booking.getMenteeId());

        return ScheduleDetailResponse.from(
                booking,
                mentorNickname,
                menteeNickname,
                scheduleTime,
                booking.getRole(memberId)
        );
    }

    public Page<ScheduleListResponse> searchPastSchedules(String memberId, CalendarScheduleSearchCondition condition) {
        CalendarScheduleSearchCondition past = condition
                .participate(memberId)
                .toPast(condition.to());

        return search(past);
    }

    public Page<ScheduleListResponse> searchUpcomingSchedules(String memberId, CalendarScheduleSearchCondition condition) {
        CalendarScheduleSearchCondition intended = condition
                .participate(memberId)
                .toIntended(condition.from());

        return search(intended);
    }

    public Page<ScheduleListResponse> calendar(String memberId, Instant start, Instant end, Integer page, Integer size) {
        CalendarScheduleSearchCondition condition = CalendarScheduleSearchCondition.defaultCondition()
                .participate(memberId)
                .between(start, end)
                .withSortType(CalendarScheduleSortType.START_TIME_ASC)
                .withPage(page)
                .withSize(size);

        return search(condition);
    }

    public Page<ScheduleListResponse> search(CalendarScheduleSearchCondition condition) {
        Sort sort = condition.sortType().toSort();
        PageRequest pageRequest =
                PageRequest.of(condition.page(), condition.size(), sort);

        Specification<ScheduleTime> spec =
                CalendarScheduleSpecifications.search(
                        condition.keyword(),
                        condition.from(),
                        condition.to(),
                        condition.status(),
                        condition.participantRole() == ScheduleParticipantRole.MENTOR || condition.participantRole() == ScheduleParticipantRole.BOTH
                                ? condition.memberId() : null,
                        condition.participantRole() == ScheduleParticipantRole.MENTEE || condition.participantRole() == ScheduleParticipantRole.BOTH
                                ? condition.memberId() : null,
                        condition.participantRole()
                );

        Page<ScheduleTime> page = scheduleTimeRepository.findAll(spec, pageRequest);

        return convert(condition.memberId(), page);
    }

    public Page<ScheduleListResponse> convert(String memberId, Page<ScheduleTime> page) {
        Set<Schedule> bookings = page.getContent().stream()
                .map(ScheduleTime::getSchedule)
                .collect(Collectors.toSet());

        Set<String> memberIds =
                bookings.stream()
                        .flatMap(booking -> Stream.of(booking.getMentorId(), booking.getMenteeId()))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        Map<String, ProfileResponseDto> members = memberService.getAllProfilesByMemberIds(memberIds);

        return page.map(
                scheduleTime -> {
                    Schedule booking = scheduleTime.getSchedule();
                    ProfileResponseDto mentor = members.get(booking.getMentorId());
                    ProfileResponseDto mentee = members.get(booking.getMenteeId());

                    return ScheduleListResponse.from(
                            booking,
                            mentor.nickname(),
                            mentee.nickname(),
                            scheduleTime,
                            booking.getRole(memberId)
                    );
                }
        );
    }
}
