package com.company.hr.attendance.attendance.api;

import com.company.hr.attendance.attendance.api.AttendanceExplanationModels.*;
import com.company.hr.attendance.attendance.application.*;
import com.company.hr.attendance.attendance.domain.AttendanceExplanation.Status;
import com.company.hr.attendance.shared.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated
@RequestMapping("/api/v1/attendance/explanations")
public class AttendanceExplanationController {
    private final AttendanceExplanationService service;
    public AttendanceExplanationController(AttendanceExplanationService service){this.service=service;}
    private UUID actor(Jwt jwt){return UUID.fromString(jwt.getSubject());}
    @PostMapping @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<ExplanationResponse> submit(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody SubmitRequest body){
        var result=service.submit(actor(jwt),body);
        return ResponseEntity.created(URI.create("/api/v1/attendance/explanations/"+result.id())).cacheControl(CacheControl.noStore()).body(result);
    }
    @GetMapping("/candidates") @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<Page<Candidate>> candidates(@AuthenticationPrincipal Jwt jwt,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.candidates(actor(jwt),from,to,page,size));
    }
    @GetMapping("/mine") @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<Page<ExplanationResponse>> mine(@AuthenticationPrincipal Jwt jwt,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.mine(actor(jwt),from,to,page,size));
    }
    @GetMapping @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<Page<ExplanationResponse>> managed(@AuthenticationPrincipal Jwt jwt,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required=false) Status status,
        @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.managed(actor(jwt),from,to,status,page,size));
    }
    @PatchMapping("/{id}/decision") @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ExplanationResponse> decide(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody DecisionRequest body){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.decide(actor(jwt),id,body));
    }
    @ExceptionHandler(AttendanceException.class)
    public ResponseEntity<ApiError> domain(AttendanceException error,HttpServletRequest request){return ResponseEntity.status(error.getStatus()).cacheControl(CacheControl.noStore()).body(ApiError.of(error.getCode(),error.getMessage(),request.getRequestURI()));}
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.web.method.annotation.HandlerMethodValidationException.class,
        jakarta.validation.ConstraintViolationException.class,org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> invalid(Exception error,HttpServletRequest request){return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(ApiError.of("INVALID_EXPLANATION_REQUEST","Thông tin giải trình hoặc bộ lọc không hợp lệ.",request.getRequestURI()));}
}
