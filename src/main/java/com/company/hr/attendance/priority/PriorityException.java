package com.company.hr.attendance.priority;

import org.springframework.http.HttpStatus;

public class PriorityException extends RuntimeException {
    private final HttpStatus status; private final String code;
    public PriorityException(HttpStatus status,String code,String message){super(message);this.status=status;this.code=code;}
    public HttpStatus status(){return status;}
    public String code(){return code;}
}
