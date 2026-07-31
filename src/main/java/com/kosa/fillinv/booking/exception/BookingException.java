package com.kosa.fillinv.booking.exception;

import com.kosa.fillinv.global.exception.CustomGlobalException;
import com.kosa.fillinv.global.response.ErrorCode;
import lombok.Getter;

@Getter
public class BookingException extends CustomGlobalException {

    public BookingException(ErrorCode errorCode) {
        super(errorCode);
    }

    public static class BookingNotFound extends BookingException {
        public BookingNotFound() {
            super(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
