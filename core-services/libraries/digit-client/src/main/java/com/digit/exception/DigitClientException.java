package com.digit.exception;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class DigitClientException extends RuntimeException {

    private final HttpStatusCode httpStatus;
    private final String errorCode;

    public DigitClientException(String message) {
        super(message);
        this.httpStatus = org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
        this.errorCode = "DIGIT_CLIENT_ERROR";
    }

    public DigitClientException(String message, HttpStatusCode httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = "DIGIT_CLIENT_ERROR";
    }

    public DigitClientException(String message, HttpStatusCode httpStatus, String errorCode) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public DigitClientException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
        this.errorCode = "DIGIT_CLIENT_ERROR";
    }

    public DigitClientException(String message, Throwable cause, HttpStatusCode httpStatus, String errorCode) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }
}
