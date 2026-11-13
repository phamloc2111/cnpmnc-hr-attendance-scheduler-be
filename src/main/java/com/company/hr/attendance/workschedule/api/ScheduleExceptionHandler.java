package com.company.hr.attendance.workschedule.api;

import com.company.hr.attendance.workschedule.application.ScheduleException;
import com.company.hr.attendance.shared.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;

@RestControllerAdvice(assignableTypes = ScheduleController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ScheduleExceptionHandler {
    @ExceptionHandler(ScheduleException.class)
    ResponseEntity<ApiError> operation(ScheduleException error, HttpServletRequest request) {
        return ResponseEntity.status(error.getStatus()).body(ApiError.of(error.getCode(), error.getMessage(), request.getRequestURI()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
        ConstraintViolationException.class, MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class})
    ResponseEntity<ApiError> validation(Exception error, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_ERROR",
            "Vui lòng kiểm tra dữ liệu ca hoặc tuần, ngày và mẫu ca cần xếp.", request.getRequestURI()));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(Exception error, HttpServletRequest request) {
        return ResponseEntity.status(409).body(ApiError.of("SCHEDULE_CONFLICT",
            "Lịch làm việc bị trùng hoặc vừa thay đổi. Vui lòng tải lại.", request.getRequestURI()));
    }
}
