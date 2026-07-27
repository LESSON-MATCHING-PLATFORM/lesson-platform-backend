package com.kosa.fillinv.schedule.controller;

import com.kosa.fillinv.global.exception.BusinessException;
import com.kosa.fillinv.global.response.ErrorCode;
import com.kosa.fillinv.global.response.SuccessResponse;
import com.kosa.fillinv.global.security.details.CustomMemberDetails;
import com.kosa.fillinv.schedule.dto.request.ScheduleCreateRequest;
import com.kosa.fillinv.schedule.dto.response.CreateScheduleResponse;
import com.kosa.fillinv.schedule.dto.response.ScheduleDetailResponse;
import com.kosa.fillinv.schedule.dto.response.ScheduleListResponse;
import com.kosa.fillinv.schedule.entity.ScheduleStatus;
import com.kosa.fillinv.schedule.service.ScheduleCommandService;
import com.kosa.fillinv.schedule.service.ScheduleReadService;
import com.kosa.fillinv.schedule.service.dto.ScheduleSearchCondition;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/schedules")
public class ScheduleController {

    private final ScheduleCommandService scheduleCommandService;
    private final ScheduleReadService scheduleReadService;

    // 스케쥴 생성
    @PostMapping
    public ResponseEntity<SuccessResponse<CreateScheduleResponse>> createSchedule(
            @AuthenticationPrincipal CustomMemberDetails customMemberDetails, // 로그인한 사용자 ID
            @RequestBody ScheduleCreateRequest request
    ) {
        String memberId = customMemberDetails.memberId();

        String scheduleId = scheduleCommandService.createSchedule(memberId, request);

        // 요청 주소 - ServletUriComponentsBuilder 사용 시 서버 주소가 바뀌더라도 코드를 수정하지 않아도 됨
        // 멘토, 멘티의 스케쥴 상세 보기 주소를 Location 헤더에 담아주기
        java.net.URI location = ServletUriComponentsBuilder
                .fromCurrentRequest() // 현재 요청 주소 가져오기
                .path("/{id}") // /{id} 추가
                .buildAndExpand(scheduleId) // {id} 자리에 scheduleId 넣기
                .toUri(); // URI로 변환

        return ResponseEntity
                .created(location) // Created 응답 시 Body 대신 Location 헤더에 리소스 URI 반환
                .body(SuccessResponse.success(HttpStatus.CREATED, new CreateScheduleResponse(scheduleId)));
    }

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
            @RequestParam(required = false) ScheduleStatus status,
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
            @RequestParam(required = false) ScheduleStatus status,
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
    public ResponseEntity<SuccessResponse<Page<ScheduleListResponse>>> calendarSchedules(
            @AuthenticationPrincipal CustomMemberDetails customMemberDetails, // 로그인한 사용자 ID
            @RequestParam Instant start,
            @RequestParam Instant end,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "1000") Integer size
    ) {
        String memberId = customMemberDetails.memberId();

        Page<ScheduleListResponse> responses = scheduleReadService.calendar(
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

    // 스케쥴 상태 변경 (PATCH)
    // 멘토의 멘티 수강신청 승인/거절 처리 (승인 대기인 상태일 경우 해당 상태 변경 가능)
    @PatchMapping("/{scheduleId}/status")
    public ResponseEntity<SuccessResponse<Void>> updateStatus(
            @AuthenticationPrincipal CustomMemberDetails customMemberDetails,
            @PathVariable String scheduleId,
            @RequestParam ScheduleStatus next
    ) {
        String memberId = customMemberDetails.memberId();

        switch (next) {
            case APPROVED -> scheduleCommandService.approveLessonByMentor(memberId, scheduleId);
            case CANCELED -> scheduleCommandService.rejectLessonByMentor(memberId, scheduleId);
            case COMPLETED -> scheduleCommandService.completeLesson(memberId, scheduleId);
            default -> throw new BusinessException(ErrorCode.INVALID_SCHEDULE_STATUS);
        }

        return ResponseEntity.ok(SuccessResponse.success(HttpStatus.OK));
    }
}