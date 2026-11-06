package com.company.hr.attendance.organization.api;
import com.company.hr.attendance.organization.application.PositionException;
import com.company.hr.attendance.shared.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PositionExceptionHandler {
    @ExceptionHandler(PositionException.class)
    ResponseEntity<ApiError> operation(PositionException error,HttpServletRequest request) {
        return ResponseEntity.status(error.getStatus()).body(ApiError.of(error.getCode(),error.getMessage(),request.getRequestURI()));
    }
}
