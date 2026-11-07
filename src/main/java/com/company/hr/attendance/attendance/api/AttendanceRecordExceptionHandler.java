package com.company.hr.attendance.attendance.api;

import com.company.hr.attendance.attendance.application.AttendanceException;
import com.company.hr.attendance.shared.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = AttendanceRecordController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AttendanceRecordExceptionHandler {
    @ExceptionHandler(AttendanceException.class)
    ResponseEntity<ApiError> domain(AttendanceException error, HttpServletRequest request) {
        return ResponseEntity.status(error.getStatus())
            .body(ApiError.of(error.getCode(), error.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        ConstraintViolationException.class,
        MethodArgumentTypeMismatchException.class,
        HandlerMethodValidationException.class,
        MissingServletRequestParameterException.class
    })
    ResponseEntity<ApiError> validation(Exception error, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(
            "VALIDATION_ERROR",
            "Dữ liệu lượt điểm danh hoặc bộ lọc không hợp lệ.",
            request.getRequestURI()
        ));
    }
}
