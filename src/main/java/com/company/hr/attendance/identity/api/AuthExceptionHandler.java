package com.company.hr.attendance.identity.api;

import com.company.hr.attendance.identity.application.LoginException;
import com.company.hr.attendance.shared.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {
    @ExceptionHandler(LoginException.class)
    ResponseEntity<ApiError> login(LoginException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.isLocked() ? 403 : 401)
            .body(ApiError.of(exception.isLocked() ? "ACCOUNT_LOCKED" : "INVALID_CREDENTIALS",
                exception.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiError> validation(Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_ERROR",
            "Vui lòng nhập email hợp lệ và mật khẩu (tối đa 72 byte).", request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException exception, HttpServletRequest request) {
        return ResponseEntity.status(403).body(ApiError.of("FORBIDDEN",
            "Bạn không có quyền truy cập chức năng này", request.getRequestURI()));
    }
}
