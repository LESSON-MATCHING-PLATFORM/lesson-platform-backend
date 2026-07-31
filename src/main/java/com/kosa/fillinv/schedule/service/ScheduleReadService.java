package com.kosa.fillinv.schedule.service;

import com.kosa.fillinv.booking.entity.BookingSession;
import com.kosa.fillinv.booking.repository.BookingSessionRepository;
import com.kosa.fillinv.schedule.repository.ScheduleSpecifications;
import com.kosa.fillinv.schedule.service.dto.ScheduleSearchCondition;
import com.kosa.fillinv.schedule.service.dto.ScheduleSortType;
import com.kosa.fillinv.global.exception.BusinessException;
import com.kosa.fillinv.global.response.ErrorCode;
import com.kosa.fillinv.member.dto.profile.ProfileResponseDto;
import com.kosa.fillinv.member.service.MemberService;
import com.kosa.fillinv.schedule.dto.response.ScheduleDetailResponse;
import com.kosa.fillinv.schedule.dto.response.ScheduleListResponse;
import com.kosa.fillinv.booking.entity.Booking;
import com.kosa.fillinv.schedule.repository.ScheduleParticipantRole;
import com.kosa.fillinv.booking.service.BookingValidator;
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

    private final BookingSessionRepository bookingSessionRepository;
    private final MemberService memberService;
    private final BookingValidator validator;

    public ScheduleDetailResponse getScheduleDetail(String memberId, String bookingId, String scheduleTimeId) {
        Booking booking = validator.getBooking(bookingId);
        BookingSession session = validator.getBookingSession(scheduleTimeId);

        if (!session.getBooking().getId().equals(bookingId)) {
            throw new BusinessException(ErrorCode.SCHEDULE_TIME_MISMATCH);
        }

        String mentorNickname = booking.getMentorNickname();
        String menteeNickname = validator.getNickname(booking.getMenteeId());

        return ScheduleDetailResponse.from(
                booking,
                mentorNickname,
                menteeNickname,
                session,
                booking.getRole(memberId)
        );
    }

    public Page<ScheduleListResponse> searchPastSchedules(String memberId, ScheduleSearchCondition condition) {
        ScheduleSearchCondition past = condition
                .participate(memberId)
                .toPast(condition.to());

        return search(past);
    }

    public Page<ScheduleListResponse> searchUpcomingSchedules(String memberId, ScheduleSearchCondition condition) {
        ScheduleSearchCondition intended = condition
                .participate(memberId)
                .toIntended(condition.from());

        return search(intended);
    }

    public Page<ScheduleListResponse> searchSchedulesBetween(String memberId, Instant start, Instant end, Integer page, Integer size) {
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

        Specification<BookingSession> spec =
                ScheduleSpecifications.search(
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

        Page<BookingSession> page = bookingSessionRepository.findAll(spec, pageRequest);

        return convert(condition.memberId(), page);
    }

    public Page<ScheduleListResponse> convert(String memberId, Page<BookingSession> page) {
        Set<Booking> bookings = page.getContent().stream()
                .map(BookingSession::getBooking)
                .collect(Collectors.toSet());

        Set<String> memberIds =
                bookings.stream()
                        .flatMap(booking -> Stream.of(booking.getMentorId(), booking.getMenteeId()))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        Map<String, ProfileResponseDto> members = memberService.getAllProfilesByMemberIds(memberIds);

        return page.map(
                session -> {
                    Booking booking = session.getBooking();
                    ProfileResponseDto mentor = members.get(booking.getMentorId());
                    ProfileResponseDto mentee = members.get(booking.getMenteeId());

                    return ScheduleListResponse.from(
                            booking,
                            mentor.nickname(),
                            mentee.nickname(),
                            session,
                            booking.getRole(memberId)
                    );
                }
        );
    }
}
