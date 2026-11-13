package com.company.hr.attendance.workschedule.api;

import com.company.hr.attendance.workschedule.api.ScheduleModels.*;
import com.company.hr.attendance.workschedule.application.ScheduleService;
import com.company.hr.attendance.workschedule.application.ScheduleGenerationService;
import com.company.hr.attendance.workschedule.application.ScheduleDraftWarningsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Validated
public class ScheduleController {
    private final ScheduleService service;
    private final ScheduleGenerationService generation;
    private final ScheduleDraftWarningsService draftWarnings;
    public ScheduleController(ScheduleService service, ScheduleGenerationService generation,
        ScheduleDraftWarningsService draftWarnings) {
        this.service = service; this.generation = generation; this.draftWarnings = draftWarnings;
    }
    @GetMapping("/shift-templates")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public PageResponse<ShiftResponse> shifts(@AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.shifts(actor(jwt), page, size);
    }
    @PostMapping("/shift-templates")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ShiftResponse> createShift(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateShift request) {
        var result = service.createShift(actor(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/shift-templates/" + result.id())).body(result);
    }
    @GetMapping("/work-schedules")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','ADMIN')")
    public PageResponse<ScheduleResponse> list(@AuthenticationPrincipal Jwt jwt,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) UUID employeeId,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(actor(jwt), from, to, employeeId, page, size);
    }
    @PostMapping("/work-schedules")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ScheduleResponse> assign(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AssignShift request) {
        var result = service.assign(actor(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/work-schedules/" + result.id())).body(result);
    }
    @PostMapping("/work-schedules/drafts")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public DraftGenerationResponse generateDraft(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GenerateDraft request) {
        return generation.generateDraft(actor(jwt), request);
    }
    @GetMapping("/work-schedules/draft-warnings")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public DraftWarningsResponse draftWarnings(@AuthenticationPrincipal Jwt jwt,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
        @RequestParam(required = false) UUID managerId) {
        return draftWarnings.warnings(actor(jwt), weekStart, managerId);
    }
    @PostMapping("/work-schedules/draft-assignments")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ScheduleResponse> assignDraft(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AssignShift request) {
        var result = service.assignDraft(actor(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/work-schedules/" + result.id())).body(result);
    }
    @DeleteMapping("/work-schedules/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.cancel(actor(jwt), id); return ResponseEntity.noContent().build();
    }
    private UUID actor(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
