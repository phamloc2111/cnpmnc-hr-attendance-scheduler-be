package com.company.hr.attendance.shiftpass.api;

import com.company.hr.attendance.shiftpass.api.ShiftPassModels.*;
import com.company.hr.attendance.shiftpass.application.ShiftPassService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shift-passes")
@Validated
public class ShiftPassController {
    private final ShiftPassService service;

    public ShiftPassController(ShiftPassService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<ShiftPassResponse> create(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreateShiftPassRequest request
    ) {
        var created = service.postPass(UUID.fromString(jwt.getSubject()), request);
        return ResponseEntity.created(URI.create("/api/v1/shift-passes/" + created.id())).body(created);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<List<ShiftPassResponse>> mine(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(service.myPasses(UUID.fromString(jwt.getSubject())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.cancelPass(UUID.fromString(jwt.getSubject()), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/notifications")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<ShiftPassNotificationResponse>> notifications(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(service.myNotifications(UUID.fromString(jwt.getSubject())));
    }
}
