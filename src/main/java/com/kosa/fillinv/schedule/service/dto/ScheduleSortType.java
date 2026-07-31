package com.kosa.fillinv.schedule.service.dto;

import com.kosa.fillinv.global.exception.BusinessException;
import com.kosa.fillinv.global.response.ErrorCode;
import lombok.Getter;
import org.springframework.data.domain.Sort;

@Getter
public enum ScheduleSortType {
    START_TIME_ASC("startTime", Sort.Direction.ASC),
    START_TIME_DESC("startTime", Sort.Direction.DESC);

    private final String property;
    private final Sort.Direction direction;

    ScheduleSortType(String property, Sort.Direction direction) {
        this.property = property;
        this.direction = direction;
    }

    public static ScheduleSortType from(String value) {
        if (value == null || value.isBlank()) {
            return START_TIME_ASC;
        }

        try {
            return ScheduleSortType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT);
        }
    }

    public Sort toSort() {
        return Sort.by(direction, property);
    }
}
