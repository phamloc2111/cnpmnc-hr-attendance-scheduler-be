package com.company.hr.attendance.attendance.api;

import com.company.hr.attendance.attendance.application.AttendanceException;
import com.company.hr.attendance.shared.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = AttendanceController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AttendanceExceptionHandler {
    @ExceptionHandler(AttendanceException.class)
    ResponseEntity<ApiError> operation(AttendanceException error, HttpServletRequest request) {
        return ResponseEntity.status(error.getStatus()).body(ApiError.of(error.getCode(), error.getMessage(), request.getRequestURI()));
    }
    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
        MethodArgumentTypeMismatchException.class, MaxUploadSizeExceededException.class,
        ConstraintViolationException.class, HandlerMethodValidationException.class})
    ResponseEntity<ApiError> invalid(Exception error, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_ATTENDANCE",
            "Cần ảnh JPEG/PNG dưới 5 MB, ca làm việc, lượt vào/ra và vị trí hiện tại hợp lệ.", request.getRequestURI()));
    }
}
