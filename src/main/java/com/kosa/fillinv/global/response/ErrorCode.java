package com.kosa.fillinv.global.response;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Server Error
    SERVER_ERROR(INTERNAL_SERVER_ERROR, "S01", "서버 오류가 발생했습니다."),

    //Global Error
    RESOURCE_NOT_FOUND(NOT_FOUND, "G01", "요청한 자원을 찾을 수 없습니다"),
    NOT_SUPPORTED(BAD_REQUEST, "G02", "지원하지 않는 요청입니다"),
    INVALID_ARGUMENT(BAD_REQUEST, "G03", "인자가 올바르지 않습니다."),

    // Member Error
    MEMBER_NOT_FOUND(NOT_FOUND, "M01", "회원을 찾을 수 없습니다."),
    EMAIL_DUPLICATION(BAD_REQUEST, "M02", "이미 존재하는 이메일입니다."),
    NICKNAME_DUPLICATION(BAD_REQUEST, "M03", "이미 존재하는 닉네임입니다."),
    PHONE_NUM_DUPLICATION(BAD_REQUEST, "M04", "이미 존재하는 전화번호입니다."),
    PROFILE_NOT_FOUND(NOT_FOUND, "M05", "프로필을 찾을 수 없습니다."),

    // Category Error
    CATEGORY_NOT_FOUND(NOT_FOUND, "C01", "카테고리를 찾을 수 없습니다."),

    // Security Error
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "S01", "로그인에 실패했습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "S02", "유효하지 않은 토큰입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "S03", "인증되지 않은 사용자입니다."),
    ACCESS_DENIED(FORBIDDEN, "S04", "접근 권한이 없습니다."),

    // Booking/Schedule Error
    LESSON_NOT_FOUND(NOT_FOUND, "SCH01", "해당 레슨을 찾을 수 없습니다."),
    OPTION_NOT_FOUND(NOT_FOUND, "SCH02", "해당 옵션을 찾을 수 없습니다."),
    AVAILABLE_TIME_NOT_FOUND(NOT_FOUND, "SCH03", "선택한 시간대 정보를 찾을 수 없습니다"),
    INVALID_LESSON_TYPE(NOT_FOUND, "SCH04", "레슨 유형이 올바르지 않습니다."),
    MENTOR_NOT_FOUND(NOT_FOUND, "SCH05", "해당 멘토를 찾을 수 없습니다."),
    SCHEDULE_NOT_FOUND(NOT_FOUND, "SCH06", "해당 스케쥴을 찾을 수 없습니다."),
    SCHEDULE_TIME_NOT_FOUND(NOT_FOUND, "SCH07", "해당 스케쥴 시간을 찾을 수 없습니다."),
    SCHEDULE_TIME_MISMATCH(NOT_FOUND, "SCH08", "스케쥴과 스케쥴 시간이 일치하지 않습니다."),
    INVALID_SCHEDULE_STATUS(NOT_FOUND, "SCH09", "스케쥴 상태가 올바르지 않습니다."),
    INVALID_DATE_FORMAT(NOT_FOUND, "SCH10", "날짜 형식이 올바르지 않습니다."),
    NO_SEAT(BAD_REQUEST, "SCH11", "남은 좌석이 없습니다."),

    // Review Error
    REVIEW_NOT_ALLOWED(BAD_REQUEST, "R01", "리뷰를 작성할 수 없는 상태입니다."),
    REVIEW_ALREADY_EXISTS(BAD_REQUEST, "R02", "이미 리뷰를 작성했습니다."),
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
