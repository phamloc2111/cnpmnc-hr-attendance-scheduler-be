package com.company.hr.attendance.registration.api;

import com.company.hr.attendance.registration.application.RegistrationService;
import com.company.hr.attendance.registration.api.RegistrationModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
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

@RestController
@RequestMapping("/api/v1/registrations")
@PreAuthorize("hasRole('EMPLOYEE')")
@Validated
public class RegistrationController {
    private final RegistrationService service;
    public RegistrationController(RegistrationService service) { this.service = service; }
    @GetMapping("/me") public ResponseEntity<Week> get(@AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return fresh(service.get(actor(jwt), weekStart));
    }
    @PutMapping("/me") public ResponseEntity<Week> save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Save request) {
        return fresh(service.save(actor(jwt), request));
    }
    @GetMapping("/shifts") public ResponseEntity<Catalog> catalog(@AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return fresh(service.catalog(actor(jwt), page, size));
    }
    private UUID actor(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    private <T> ResponseEntity<T> fresh(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
