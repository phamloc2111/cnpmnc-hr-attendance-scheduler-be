package com.company.hr.attendance.organization.api;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;

@RestControllerAdvice(assignableTypes=PositionController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PositionValidationHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,ConstraintViolationException.class,
        MethodArgumentTypeMismatchException.class,HandlerMethodValidationException.class})
    ResponseEntity<ApiError> invalid(Exception error,HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_ERROR","Vui lòng kiểm tra tên vị trí và tham số danh sách.",request.getRequestURI()));
    }
    @ExceptionHandler({DataIntegrityViolationException.class,ObjectOptimisticLockingFailureException.class})
    ResponseEntity<ApiError> conflict(Exception error,HttpServletRequest request) {
        return ResponseEntity.status(409).body(ApiError.of("POSITION_CONFLICT","Vị trí bị trùng hoặc vừa thay đổi. Vui lòng tải lại.",request.getRequestURI()));
    }
}
