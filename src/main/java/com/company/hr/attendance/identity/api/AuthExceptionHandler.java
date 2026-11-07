package com.company.hr.attendance.identity.api;

import com.company.hr.attendance.identity.application.AccountOperationException;
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

    @ExceptionHandler(AccountOperationException.class)
    ResponseEntity<ApiError> operation(AccountOperationException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus())
            .body(ApiError.of(exception.getCode(), exception.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiError> validation(Exception exception, HttpServletRequest request) {
        var uri = request.getRequestURI();
        var message = uri.endsWith("/change-password") || uri.endsWith("/password")
            ? "Vui lòng nhập mật khẩu cũ và mật khẩu mới từ 8 ký tự (tối đa 72 byte UTF-8)."
            : "Vui lòng nhập email hợp lệ và mật khẩu (tối đa 72 byte).";
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_ERROR", message, uri));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException exception, HttpServletRequest request) {
        return ResponseEntity.status(403).body(ApiError.of("FORBIDDEN",
            "Bạn không có quyền truy cập chức năng này", request.getRequestURI()));
    }
}
