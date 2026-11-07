package com.company.hr.attendance.attendance.application;

import org.springframework.http.HttpStatus;

public class AttendanceException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public AttendanceException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
