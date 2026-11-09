package com.company.hr.attendance.organization.api;

import com.company.hr.attendance.organization.application.WorkplaceService;
import com.company.hr.attendance.organization.api.WorkplaceModels.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workplace-settings")
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class WorkplaceController {
    private final WorkplaceService service;
    public WorkplaceController(WorkplaceService service) { this.service = service; }
    @GetMapping public ResponseEntity<WorkplaceResponse> get(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(UUID.fromString(jwt.getSubject())));
    }
    @PutMapping public ResponseEntity<WorkplaceResponse> save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SaveWorkplace body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.save(UUID.fromString(jwt.getSubject()), body));
    }
}
