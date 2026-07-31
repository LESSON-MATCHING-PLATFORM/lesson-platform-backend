package com.kosa.fillinv.global.exception;

import com.kosa.fillinv.global.response.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class CustomGlobalException extends RuntimeException {

    private final ErrorCode errorCode;

    // 메세지 직접 수정 시 사용
    protected CustomGlobalException(final ErrorCode errorCode, final String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected CustomGlobalException(final ErrorCode errorCode, final Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    //  ErrorCode 기반 메세지 사용 시
    protected CustomGlobalException(final ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public HttpStatus getHttpStatus() {
        return errorCode.getHttpStatus();
    }
}
