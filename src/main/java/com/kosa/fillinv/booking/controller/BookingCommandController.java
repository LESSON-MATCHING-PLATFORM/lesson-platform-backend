package com.kosa.fillinv.booking.controller;

import com.kosa.fillinv.booking.dto.request.BookingCreateRequest;
import com.kosa.fillinv.booking.dto.response.BookingCreateResponse;
import com.kosa.fillinv.booking.entity.BookingStatus;
import com.kosa.fillinv.booking.service.BookingCommandService;
import com.kosa.fillinv.global.exception.BusinessException;
import com.kosa.fillinv.global.response.ErrorCode;
import com.kosa.fillinv.global.response.SuccessResponse;
import com.kosa.fillinv.global.security.details.CustomMemberDetails;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
public class BookingCommandController {

    private final BookingCommandService bookingCommandService;

    @PostMapping
    public ResponseEntity<SuccessResponse<BookingCreateResponse>> createBooking(
            @AuthenticationPrincipal CustomMemberDetails customMemberDetails,
            @RequestBody BookingCreateRequest request
    ) {
        String memberId = customMemberDetails.memberId();

        String bookingId = bookingCommandService.createBooking(memberId, request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(bookingId)
                .toUri();

        return ResponseEntity
                .created(location)
                .body(SuccessResponse.success(HttpStatus.CREATED, new BookingCreateResponse(bookingId)));
    }

    @PatchMapping("/{scheduleId}/status")
    public ResponseEntity<SuccessResponse<Void>> updateStatus(
            @AuthenticationPrincipal CustomMemberDetails customMemberDetails,
            @PathVariable String scheduleId,
            @RequestParam BookingStatus next
    ) {
        String memberId = customMemberDetails.memberId();

        switch (next) {
            case APPROVED -> bookingCommandService.approveLessonByMentor(memberId, scheduleId);
            case CANCELED -> bookingCommandService.rejectLessonByMentor(memberId, scheduleId);
            case COMPLETED -> bookingCommandService.completeLesson(memberId, scheduleId);
            default -> throw new BusinessException(ErrorCode.INVALID_SCHEDULE_STATUS);
        }

        return ResponseEntity.ok(SuccessResponse.success(HttpStatus.OK));
    }
}
