package com.company.hr.attendance.identity.api;

import com.company.hr.attendance.identity.application.AccountOperationException;
import com.company.hr.attendance.shared.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice(assignableTypes = AccountController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccountExceptionHandler {
    @ExceptionHandler(AccountOperationException.class)
    ResponseEntity<ApiError> operation(AccountOperationException error, HttpServletRequest request) {
        return ResponseEntity.status(error.getStatus()).body(ApiError.of(error.getCode(), error.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
        ConstraintViolationException.class, MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class})
    ResponseEntity<ApiError> validation(Exception error, HttpServletRequest request) {
        String message = "Dữ liệu không hợp lệ. Vui lòng kiểm tra các trường và tham số phân trang";
        if (error instanceof MethodArgumentNotValidException invalid && invalid.getBindingResult().hasFieldErrors()) {
            message = "Vui lòng kiểm tra thông tin tài khoản: " + invalid.getBindingResult().getFieldErrors().getFirst().getField();
        }
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_ERROR", message, request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> duplicate(DataIntegrityViolationException error, HttpServletRequest request) {
        return ResponseEntity.status(409).body(ApiError.of("ACCOUNT_CONFLICT",
            "Thông tin tài khoản bị trùng hoặc đã thay đổi. Vui lòng kiểm tra lại", request.getRequestURI()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> stale(ObjectOptimisticLockingFailureException error, HttpServletRequest request) {
        return ResponseEntity.status(409).body(ApiError.of("STALE_ACCOUNT",
            "Tài khoản vừa được cập nhật. Vui lòng tải lại dữ liệu trước khi lưu", request.getRequestURI()));
    }
}
