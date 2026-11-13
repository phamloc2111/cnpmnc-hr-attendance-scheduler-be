package com.company.hr.attendance.leave.api;

import com.company.hr.attendance.leave.api.LeaveModels.*;
import com.company.hr.attendance.leave.application.LeaveService;
import com.company.hr.attendance.leave.domain.LeaveStatus;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leave-requests")
public class LeaveController {

    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    @PostMapping
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<LeaveResponse> submit(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreateLeaveRequest request
    ) {
        UUID employeeId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(leaveService.submit(employeeId, request));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<List<LeaveResponse>> getMyLeaves(@AuthenticationPrincipal Jwt jwt) {
        UUID employeeId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(leaveService.getMyLeaves(employeeId));
    }

    @GetMapping("/quota")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<LeaveQuotaResponse> getQuota(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) String month
    ) {
        UUID employeeId = UUID.fromString(jwt.getSubject());
        YearMonth ym;
        if (month != null && !month.isBlank()) {
            try {
                ym = YearMonth.parse(month.strip());
            } catch (DateTimeParseException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Định dạng tháng không hợp lệ (YYYY-MM).");
            }
        } else {
            ym = YearMonth.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        }
        return ResponseEntity.ok(leaveService.getQuota(employeeId, ym));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<PageResponse<LeaveResponse>> listRequests(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) LeaveStatus status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) UUID employeeId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(leaveService.listRequests(actorId, status, from, to, employeeId, page, size));
    }

    @PutMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<LeaveResponse> review(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID id,
        @Valid @RequestBody ReviewLeaveRequest request
    ) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(leaveService.review(actorId, id, request));
    }
}
