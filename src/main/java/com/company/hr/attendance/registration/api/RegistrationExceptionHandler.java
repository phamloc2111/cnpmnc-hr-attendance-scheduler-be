package com.company.hr.attendance.registration.api;

import com.company.hr.attendance.registration.application.RegistrationException;
import com.company.hr.attendance.shared.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice(assignableTypes = RegistrationController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RegistrationExceptionHandler {
    @ExceptionHandler(RegistrationException.class) ResponseEntity<ApiError> operation(RegistrationException error, HttpServletRequest request) {
        return response(error.getStatus(), error.getCode(), error.getMessage(), request);
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
        MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiError> invalid(Exception error, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REGISTRATION", "Tuần, danh sách ca hoặc phiên bản đăng ký không hợp lệ (tối đa 100 ca).", request);
    }
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<ApiError> conflict(Exception error, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "REGISTRATION_CHANGED", "Danh sách ca vừa thay đổi. Hãy tải lại trước khi lưu.", request);
    }
    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(ApiError.of(code, message, request.getRequestURI()));
    }
}
