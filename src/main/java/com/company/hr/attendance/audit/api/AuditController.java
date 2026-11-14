package com.company.hr.attendance.audit.api;

import com.company.hr.attendance.audit.api.AuditModels.AuditLogResponse;
import com.company.hr.attendance.audit.api.AuditModels.PageResponse;
import com.company.hr.attendance.audit.application.AuditService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@RequestMapping("/api/v1/audit-logs")
@Validated
public class AuditController {

    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PageResponse<AuditLogResponse>> list(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) UUID actorId,
        @RequestParam(required = false) UUID employeeId,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        var result = service.list(
            UUID.fromString(jwt.getSubject()),
            actorId,
            employeeId,
            category,
            from,
            to,
            page,
            size
        );
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
