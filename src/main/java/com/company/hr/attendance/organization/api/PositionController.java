package com.company.hr.attendance.organization.api;
import com.company.hr.attendance.organization.api.PositionModels.*;
import com.company.hr.attendance.organization.application.PositionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/positions")
@Validated
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class PositionController {
    private final PositionService service;
    public PositionController(PositionService service) { this.service=service; }
    @GetMapping
    public PositionPage list(@AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue="") @Size(max=120) String q, @RequestParam(required=false) Boolean active,
        @RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.list(actor(jwt),q,active,page,size);
    }
    @GetMapping("/{id}")
    public PositionResponse get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) { return service.get(actor(jwt),id); }
    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<PositionResponse> create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CreatePosition body) {
        var result=service.create(actor(jwt),body);
        return ResponseEntity.created(URI.create("/api/v1/positions/"+result.id())).body(result);
    }
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('MANAGER')")
    public PositionResponse status(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody SetStatus body) {
        return service.status(actor(jwt),id,body);
    }
    private UUID actor(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
