package com.company.hr.attendance.shiftpass.application;

import org.springframework.http.HttpStatus;

public class ShiftPassException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ShiftPassException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
