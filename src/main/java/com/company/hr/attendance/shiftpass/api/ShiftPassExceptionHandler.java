package com.company.hr.attendance.shiftpass.api;

import com.company.hr.attendance.shared.api.ApiError;
import com.company.hr.attendance.shiftpass.application.ShiftPassException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = ShiftPassController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ShiftPassExceptionHandler {
    @ExceptionHandler(ShiftPassException.class)
    ResponseEntity<ApiError> operation(ShiftPassException error, HttpServletRequest request) {
        return ResponseEntity.status(error.getStatus())
            .body(ApiError.of(error.getCode(), error.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        ConstraintViolationException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiError> validation(Exception error, HttpServletRequest request) {
        return ResponseEntity.badRequest()
            .body(ApiError.of("VALIDATION_ERROR", "Dữ liệu yêu cầu pass ca không hợp lệ.", request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(Exception error, HttpServletRequest request) {
        return ResponseEntity.status(409)
            .body(ApiError.of("SHIFT_PASS_CONFLICT", "Trạng thái pass ca vừa thay đổi. Vui lòng tải lại.", request.getRequestURI()));
    }
}
