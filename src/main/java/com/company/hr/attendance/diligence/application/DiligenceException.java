package com.company.hr.attendance.diligence.application;
import org.springframework.http.HttpStatus;
public class DiligenceException extends RuntimeException {
    private final HttpStatus status; private final String code;
    public DiligenceException(HttpStatus status,String code,String message){super(message);this.status=status;this.code=code;}
    public HttpStatus status(){return status;} public String code(){return code;}
}
