package com.company.hr.attendance.attendance.api;

import com.company.hr.attendance.attendance.api.AttendanceRecordModels.*;
import com.company.hr.attendance.attendance.application.AttendanceRecordService;
import com.company.hr.attendance.attendance.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/attendance-records")
@Validated
public class AttendanceRecordController {
    private final AttendanceRecordService service;

    public AttendanceRecordController(AttendanceRecordService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','ADMIN')")
    public PageResponse<AttendanceRecordResponse> list(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) UUID employeeId,
        @RequestParam(required = false) AttendanceStatus status,
        @RequestParam(required = false) AttendanceType checkType,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return service.list(actor(jwt), from, to, employeeId, status, checkType, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','ADMIN')")
    public AttendanceRecordResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(actor(jwt), id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','ADMIN')")
    public ResponseEntity<AttendanceRecordResponse> create(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreateAttendanceRequest request
    ) {
        var result = service.create(actor(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/attendance-records/" + result.id())).body(result);
    }

    @GetMapping("/devices")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','ADMIN')")
    public List<RegisteredDeviceResponse> listDevices(@AuthenticationPrincipal Jwt jwt) {
        return service.listDevices(actor(jwt));
    }

    @PutMapping("/devices/{employeeId}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public RegisteredDeviceResponse registerDevice(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID employeeId,
        @Valid @RequestBody RegisterDeviceRequest request
    ) {
        return service.registerDevice(actor(jwt), employeeId, request);
    }

    @DeleteMapping("/devices/{employeeId}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<Void> resetDevice(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID employeeId
    ) {
        service.resetDevice(actor(jwt), employeeId);
        return ResponseEntity.noContent().build();
    }

    private UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
