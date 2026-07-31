package com.kosa.fillinv.global.exception;

import com.kosa.fillinv.global.response.ErrorCode;

public class BusinessException extends CustomGlobalException {

    //ErrorCode 기반 메세지 사용 시
    public BusinessException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
