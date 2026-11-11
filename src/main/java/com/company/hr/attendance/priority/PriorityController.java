package com.company.hr.attendance.priority;

import com.company.hr.attendance.priority.PriorityService.*;
import com.company.hr.attendance.shared.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated @RequestMapping("/api/v1/priority-scores")
public class PriorityController {
    public record RatingRequest(@Min(1) @Max(5) int rating) {}
    private final PriorityService service;
    public PriorityController(PriorityService service){this.service=service;}
    @GetMapping @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<Team> team(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) UUID managerId,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate week,
        @RequestParam(required=false) UUID positionId){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(service.team(UUID.fromString(jwt.getSubject()),managerId,week,positionId));
    }
    @GetMapping("/me") @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<Own> own(@AuthenticationPrincipal Jwt jwt,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate week){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.own(UUID.fromString(jwt.getSubject()),week));
    }
    @GetMapping("/weights") @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<Weights> weights(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) UUID managerId){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.getWeights(UUID.fromString(jwt.getSubject()),managerId));
    }
    @PutMapping("/weights") @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<Weights> saveWeights(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) UUID managerId,
        @RequestBody Weights value){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.saveWeights(UUID.fromString(jwt.getSubject()),managerId,value));
    }
    @GetMapping("/ratings") @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<Map<UUID,Integer>> ratings(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) UUID managerId){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.ratingsFor(UUID.fromString(jwt.getSubject()),managerId));
    }
    @PutMapping("/ratings/{employeeId}") @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<Void> rate(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) UUID managerId,
        @PathVariable UUID employeeId,@Valid @RequestBody RatingRequest value){
        service.rate(UUID.fromString(jwt.getSubject()),managerId,employeeId,value.rating());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @ExceptionHandler(PriorityException.class)
    public ResponseEntity<ApiError> invalid(PriorityException error,HttpServletRequest request){
        return ResponseEntity.status(error.status()).cacheControl(CacheControl.noStore())
            .body(ApiError.of(error.code(),error.getMessage(),request.getRequestURI()));
    }
}
