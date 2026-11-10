package com.company.hr.attendance.registration.api;

import com.company.hr.attendance.registration.api.RegistrationModels.*;
import com.company.hr.attendance.registration.application.RegistrationAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/registrations/management")
@PreAuthorize("hasRole('MANAGER')")
@Validated
public class RegistrationManagementController {
    private final RegistrationAccessService service;
    public RegistrationManagementController(RegistrationAccessService service) { this.service = service; }
    @GetMapping public ResponseEntity<ManagedWeek> list(@AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
        @RequestParam(defaultValue = "") @Size(max = 200) String q,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return fresh(service.list(UUID.fromString(jwt.getSubject()), weekStart, q, page, size));
    }
    @PutMapping("/{employeeId}/access") public ResponseEntity<ManagedEmployee> change(@AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID employeeId, @Valid @RequestBody AccessChange request) {
        return fresh(service.change(UUID.fromString(jwt.getSubject()), employeeId, request));
    }
    private <T> ResponseEntity<T> fresh(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
