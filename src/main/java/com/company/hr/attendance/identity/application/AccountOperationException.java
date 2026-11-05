package com.company.hr.attendance.identity.application;

import org.springframework.http.HttpStatus;

public class AccountOperationException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public AccountOperationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
