package com.company.hr.attendance.organization.api;

import com.company.hr.attendance.organization.application.WorkplaceException;
import com.company.hr.attendance.shared.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = {WorkplaceController.class, WorkplacePlacesController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WorkplaceExceptionHandler {
    @ExceptionHandler(WorkplaceException.class)
    ResponseEntity<ApiError> operation(WorkplaceException error, HttpServletRequest request) {
        return ResponseEntity.status(error.getStatus()).cacheControl(org.springframework.http.CacheControl.noStore()).body(ApiError.of(error.getCode(), error.getMessage(), request.getRequestURI()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, ConstraintViolationException.class})
    ResponseEntity<ApiError> invalid(Exception error, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_WORKPLACE",
            "Cần vĩ độ -90 đến 90, kinh độ -180 đến 180, bán kính 10–5000m và dung sai GPS 20–50m.", request.getRequestURI()));
    }
}
