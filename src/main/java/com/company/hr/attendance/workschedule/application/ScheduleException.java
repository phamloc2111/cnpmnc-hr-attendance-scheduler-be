package com.company.hr.attendance.workschedule.application;

import org.springframework.http.HttpStatus;

public class ScheduleException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public ScheduleException(HttpStatus status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
