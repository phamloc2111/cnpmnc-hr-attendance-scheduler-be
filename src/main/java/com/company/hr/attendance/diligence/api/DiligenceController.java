package com.company.hr.attendance.diligence.api;
import com.company.hr.attendance.diligence.application.*;
import com.company.hr.attendance.diligence.api.DiligenceModels.*;
import com.company.hr.attendance.shared.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated
@RequestMapping("/api/v1/diligence")
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class DiligenceController {
    private final DiligenceService service;
    public DiligenceController(DiligenceService service){this.service=service;}
    @GetMapping
    public ResponseEntity<MonthPage> month(@AuthenticationPrincipal Jwt jwt,
        @RequestParam(required=false) @Size(max=7) String month,@RequestParam(defaultValue="") @Size(max=200) String q,
        @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.month(UUID.fromString(jwt.getSubject()),month,q,page,size));
    }
    @GetMapping("/employees/{id}")
    public ResponseEntity<EmployeeMonth> employee(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,
        @RequestParam(required=false) @Size(max=7) String month,
        @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.employee(UUID.fromString(jwt.getSubject()),id,month,page,size));
    }
    @ExceptionHandler({jakarta.validation.ConstraintViolationException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.web.method.annotation.HandlerMethodValidationException.class})
    public ResponseEntity<ApiError> invalidParameter(Exception error,HttpServletRequest request){
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(ApiError.of("INVALID_DILIGENCE_QUERY","Tháng, mã nhân viên hoặc phân trang không hợp lệ (mỗi trang 1–100 nhân viên).",request.getRequestURI()));
    }
    @ExceptionHandler(DiligenceException.class)
    public ResponseEntity<ApiError> invalid(DiligenceException error,HttpServletRequest request){
        return ResponseEntity.status(error.status()).cacheControl(CacheControl.noStore()).body(ApiError.of(error.code(),error.getMessage(),request.getRequestURI()));
    }
}
