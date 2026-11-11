package com.company.hr.attendance.diligence.api;

import com.company.hr.attendance.diligence.application.DiligenceException;
import com.company.hr.attendance.diligence.application.DiligenceService;
import com.company.hr.attendance.diligence.api.DiligenceModels.OwnPeriod;
import com.company.hr.attendance.shared.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated
@RequestMapping("/api/v1/diligence/me")
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeeDiligenceController {
    private final DiligenceService service;
    public EmployeeDiligenceController(DiligenceService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<OwnPeriod> month(@AuthenticationPrincipal Jwt jwt,
        @RequestParam(required=false) @Size(max=7) String month) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(service.ownMonth(UUID.fromString(jwt.getSubject()), month));
    }

    @GetMapping("/week")
    public ResponseEntity<OwnPeriod> week(@AuthenticationPrincipal Jwt jwt,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(service.ownWeek(UUID.fromString(jwt.getSubject()), date));
    }

    @ExceptionHandler(DiligenceException.class)
    public ResponseEntity<ApiError> invalid(DiligenceException error,HttpServletRequest request) {
        return ResponseEntity.status(error.status()).cacheControl(CacheControl.noStore())
            .body(ApiError.of(error.code(),error.getMessage(),request.getRequestURI()));
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class,
        jakarta.validation.ConstraintViolationException.class,
        org.springframework.web.method.annotation.HandlerMethodValidationException.class})
    public ResponseEntity<ApiError> invalidParameter(Exception error,HttpServletRequest request) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
            .body(ApiError.of("INVALID_DILIGENCE_PERIOD","Tháng hoặc tuần không hợp lệ.",request.getRequestURI()));
    }
}
