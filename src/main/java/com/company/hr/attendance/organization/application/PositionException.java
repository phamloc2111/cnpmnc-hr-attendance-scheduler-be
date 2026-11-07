package com.company.hr.attendance.organization.application;
import org.springframework.http.HttpStatus;
public class PositionException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public PositionException(HttpStatus status, String code, String message) { super(message); this.status=status; this.code=code; }
    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
