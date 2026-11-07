package com.company.hr.attendance.attendance.api;

import com.company.hr.attendance.attendance.api.AttendanceModels.*;
import com.company.hr.attendance.attendance.application.AttendanceService;
import com.company.hr.attendance.attendance.application.AttendanceResultService;
import com.company.hr.attendance.attendance.api.AttendanceResults.ResultPage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/v1/attendance")
@Validated
public class AttendanceController {
    private final AttendanceService service;
    private final AttendanceResultService results;
    public AttendanceController(AttendanceService service, AttendanceResultService results) {
        this.service = service; this.results = results;
    }

    @PostMapping(value = "/events", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<EventResponse> record(@AuthenticationPrincipal Jwt jwt,
        @RequestParam UUID scheduleId, @RequestParam EventType type,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant locatedAt,
        @RequestParam Double latitude, @RequestParam Double longitude,
        @RequestParam Double accuracyMeters,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant timemarkCapturedAt,
        @RequestParam Double timemarkLatitude, @RequestParam Double timemarkLongitude,
        @RequestParam MultipartFile photo) {
        var result = service.record(UUID.fromString(jwt.getSubject()), scheduleId, type,
            locatedAt, latitude, longitude, accuracyMeters,
            new TimemarkEvidence(timemarkCapturedAt, timemarkLatitude, timemarkLongitude), photo);
        return ResponseEntity.created(URI.create("/api/v1/attendance/events/" + result.id())).body(result);
    }

    /** Reads the timestamp and location overlay only; the uploaded image is not persisted here. */
    @PostMapping(value = "/timemark-analysis", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','ADMIN')")
    public TimemarkAnalysisResponse analyzeTimemark(@RequestPart("image") MultipartFile image) {
        return service.analyzeTimemark(image);
    }

    @GetMapping("/events")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public List<EventResponse> mine(@AuthenticationPrincipal Jwt jwt,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.mine(UUID.fromString(jwt.getSubject()), from, to);
    }

    @GetMapping("/results")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResultPage results(@AuthenticationPrincipal Jwt jwt,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) UUID employeeId,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return results.results(UUID.fromString(jwt.getSubject()), from, to, employeeId, page, size);
    }

    @GetMapping("/events/{id}/photo")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','ADMIN')")
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        var photo = service.photo(UUID.fromString(jwt.getSubject()), id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .contentType(MediaType.parseMediaType(photo.contentType())).body(photo.bytes());
    }
}
