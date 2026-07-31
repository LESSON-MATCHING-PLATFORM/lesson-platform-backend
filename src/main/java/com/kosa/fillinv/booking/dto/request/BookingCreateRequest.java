package com.kosa.fillinv.booking.dto.request;

import java.time.Instant;

public record BookingCreateRequest(
        // 스케쥴 생성 (요청)
        String lessonId,
        String optionId, // Option에서 minutes 가져옴
        String availableTimeId,
        // BookingSession에 존재하므로 수정 필요
        Instant startTime // 표준 시간 적용 - option 의 minutes 더해서 endTime 계산
) {
}
