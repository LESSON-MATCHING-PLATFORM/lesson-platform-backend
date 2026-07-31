package com.kosa.fillinv.schedule.controller;

import com.kosa.fillinv.schedule.service.ScheduleReadService;
import com.kosa.fillinv.schedule.service.dto.ScheduleSearchCondition;
import com.kosa.fillinv.global.response.SuccessResponse;
import com.kosa.fillinv.global.security.details.CustomMemberDetails;
import com.kosa.fillinv.schedule.dto.response.ScheduleDetailResponse;
import com.kosa.fillinv.schedule.dto.response.ScheduleListResponse;
import com.kosa.fillinv.booking.entity.BookingStatus;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/schedules")
public class ScheduleController {

    private final ScheduleReadService scheduleReadService;

    // 스케쥴 상세 조회
    // Ex: GET /api/v1/schedules/1/times/95e3a0e6-e685-4a60-ab63-880031fd4c69
    @GetMapping("/{scheduleId}/times/{scheduleTimeId}")
    public ResponseEntity<SuccessResponse<ScheduleDetailResponse>> getScheduleDetails(
            @AuthenticationPrincipal CustomMemberDetails customMemberDetails,
            @PathVariable String scheduleId,
            @PathVariable String scheduleTimeId
    ) {
        String memberId = customMemberDetails.memberId();

        ScheduleDetailResponse response = scheduleReadService.getScheduleDetail(memberId, scheduleId, scheduleTimeId);

        return ResponseEntity
                .ok(SuccessResponse.success(HttpStatus.OK, response));
    }

    // 예정 스케줄: GET /api/v1/schedules/upcoming (현재 시간 이후, 오름차순)
    @GetMapping("/upcoming")
    public ResponseEntity<SuccessResponse<Page<ScheduleListResponse>>> getUpcomingSchedules(
            @AuthenticationPrincipal CustomMemberDetails customMemberDetails, // 로그인한 사용자 ID
            @RequestParam Instant from,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        String memberId = customMemberDetails.memberId();

        Page<ScheduleListResponse> responses = scheduleReadService.searchUpcomingSchedules(
                memberId,
                ScheduleSearchCondition.builder()
                        .from(from)
                        .keyword(keyword)
                        .status(status)
                        .page(page)
                        .size(size)
                        .build());

        return ResponseEntity
                .ok(SuccessResponse.success(HttpStatus.OK, responses));
    }

    // 과거 스케줄: GET /api/v1/schedules/past (현재 시간 이전, 내림차순)
    @GetMapping("/past")
    public ResponseEntity<SuccessResponse<Page<ScheduleListResponse>>> getPastSchedules(
            @AuthenticationPrincipal CustomMemberDetails customMemberDetails, // 로그인한 사용자 ID
            @RequestParam Instant to,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        String memberId = customMemberDetails.memberId();

        Page<ScheduleListResponse> responses = scheduleReadService.searchPastSchedules(
                memberId,
                ScheduleSearchCondition.builder()
                        .to(to)
                        .keyword(keyword)
                        .status(status)
                        .page(page)
                        .size(size)
                        .build());

        return ResponseEntity
                .ok(SuccessResponse.success(HttpStatus.OK, responses));
    }

    // 캘린더 / 스케쥴 전체 조회 (GET) - 시간순 정렬 (특정 날짜 위주)
    @GetMapping("/calendar")
    public ResponseEntity<SuccessResponse<Page<ScheduleListResponse>>> getSchedulesBetween(
            @AuthenticationPrincipal CustomMemberDetails customMemberDetails, // 로그인한 사용자 ID
            @RequestParam Instant start,
            @RequestParam Instant end,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "1000") Integer size
    ) {
        String memberId = customMemberDetails.memberId();

        Page<ScheduleListResponse> responses = scheduleReadService.searchSchedulesBetween(
                memberId,
                start,
                end,
                page,
                size
        );

        return ResponseEntity
                .ok(SuccessResponse.success(HttpStatus.OK, responses));
    }

    // 검색
    @GetMapping("/search")
    public ResponseEntity<SuccessResponse<Page<ScheduleListResponse>>> searchSchedules(
            @AuthenticationPrincipal CustomMemberDetails customMemberDetails, // 로그인한 사용자 ID
            @ModelAttribute ScheduleSearchCondition condition
    ) {
        String memberId = customMemberDetails.memberId();

        Page<ScheduleListResponse> responses = scheduleReadService.search(condition.withMemberId(memberId));

        return ResponseEntity
                .ok(SuccessResponse.success(HttpStatus.OK, responses));
    }

}
