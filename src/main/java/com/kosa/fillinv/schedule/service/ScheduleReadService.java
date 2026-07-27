package com.kosa.fillinv.schedule.service;

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
import com.kosa.fillinv.schedule.repository.ScheduleTimeSpecifications;
import com.kosa.fillinv.schedule.service.dto.ScheduleSearchCondition;
import com.kosa.fillinv.schedule.service.dto.ScheduleSortType;
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
public class ScheduleReadService {

    private final ScheduleTimeRepository scheduleTimeRepository;
    private final MemberService memberService;
    private final ScheduleValidator validator;

    // 스케쥴 상세 조회
    public ScheduleDetailResponse getScheduleDetail(String memberId, String scheduleId, String scheduleTimeId) {
        Schedule schedule = validator.getSchedule(scheduleId);
        ScheduleTime scheduleTime = validator.getScheduleTime(scheduleTimeId);

        // 스케쥴과 스케쥴 타임이 연결되어 있는지 확인
        if (!scheduleTime.getSchedule().getId().equals(scheduleId)) {
            throw new BusinessException(ErrorCode.SCHEDULE_TIME_MISMATCH);
        }

        String mentorNickname = schedule.getMentorNickname();
        String menteeNickname = validator.getNickname(schedule.getMenteeId());

        // entity -> dto 변환
        // 사용자가 선택한 시간을 보여줘야 하므로 startTime 파라미터 추가
        return ScheduleDetailResponse.from(
                schedule,
                mentorNickname,
                menteeNickname,
                scheduleTime,
                schedule.getRole(memberId)
        );
    }

    // 멤버가 멘티 또는 멘토인 과거 스케줄 검색
    public Page<ScheduleListResponse> searchPastSchedules(String memberId, ScheduleSearchCondition condition) {
        ScheduleSearchCondition past = condition
                .participate(memberId)
                .toPast(condition.to());

        return search(past);
    }

    // 멤버가 멘티 또는 멘토인 예정 스케줄 검색
    public Page<ScheduleListResponse> searchUpcomingSchedules(String memberId, ScheduleSearchCondition condition) {
        ScheduleSearchCondition intended = condition
                .participate(memberId)
                .toIntended(condition.from());

        return search(intended);
    }

    // 해당 기간에 포함되는 일정 조회
    public Page<ScheduleListResponse> calendar(String memberId, Instant start, Instant end, Integer page, Integer size) {
        ScheduleSearchCondition condition = ScheduleSearchCondition.defaultCondition()
                .participate(memberId)
                .between(start, end)
                .withSortType(ScheduleSortType.START_TIME_ASC)
                .withPage(page)
                .withSize(size);

        return search(condition);
    }

    public Page<ScheduleListResponse> search(ScheduleSearchCondition condition) {

        Sort sort = condition.sortType().toSort();
        PageRequest pageRequest =
                PageRequest.of(condition.page(), condition.size(), sort);

        Specification<ScheduleTime> spec =
                ScheduleTimeSpecifications.search(
                        condition.keyword(),
                        condition.from(),
                        condition.to(),
                        condition.status(),
                        condition.participantRole() == ScheduleParticipantRole.MENTOR || condition.participantRole() == ScheduleParticipantRole.BOTH
                                ? condition.memberId() : null,
                        condition.participantRole() == ScheduleParticipantRole.MENTEE  || condition.participantRole() == ScheduleParticipantRole.BOTH
                                ? condition.memberId() : null,
                        condition.participantRole()
                );

        Page<ScheduleTime> page = scheduleTimeRepository.findAll(spec, pageRequest);

        return convert(condition.memberId(), page);
    }

    public Page<ScheduleListResponse> convert(String memberId, Page<ScheduleTime> page) {
        Set<Schedule> schedule = page.getContent().stream()
                .map(ScheduleTime::getSchedule)
                .collect(Collectors.toSet());

        Set<String> memberIds =
                schedule.stream()
                        .flatMap(s -> Stream.of(s.getMentorId(), s.getMenteeId()))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        Map<String, ProfileResponseDto> members = memberService.getAllProfilesByMemberIds(memberIds);

        return page.map(
                scheduleTime -> {
                    Schedule s = scheduleTime.getSchedule();
                    ProfileResponseDto mentor = members.get(s.getMentorId());
                    ProfileResponseDto mentee = members.get(s.getMenteeId());

                    return ScheduleListResponse.from(
                            s,
                            mentor.nickname(),
                            mentee.nickname(),
                            scheduleTime,
                            s.getRole(memberId)
                            );
                }
        );
    }
}
