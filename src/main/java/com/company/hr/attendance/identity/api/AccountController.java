package com.company.hr.attendance.identity.api;

import com.company.hr.attendance.identity.api.AccountModels.*;
import com.company.hr.attendance.identity.application.AccountService;
import com.company.hr.attendance.identity.domain.Role;
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
@RequestMapping("/api/v1/accounts")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
@Validated
public class AccountController {
    private final AccountService service;
    public AccountController(AccountService service) { this.service = service; }

    @GetMapping
    public AccountPage list(@AuthenticationPrincipal Jwt jwt,
                            @RequestParam(defaultValue = "") @Size(max = 100) String q,
                            @RequestParam(required = false) Role role,
                            @RequestParam(required = false) Boolean locked,
                            @RequestParam(defaultValue = "0") @Min(0) int page,
                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(UUID.fromString(jwt.getSubject()), q, role, locked, page, size);
    }

    @GetMapping("/{id}")
    public AccountResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(UUID.fromString(jwt.getSubject()), id);
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody CreateRequest request) {
        var account = service.create(UUID.fromString(jwt.getSubject()), request);
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.id())).body(account);
    }

    @PutMapping("/{id}")
    public AccountResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                  @Valid @RequestBody UpdateRequest request) {
        return service.update(UUID.fromString(jwt.getSubject()), id, request);
    }

    @PatchMapping("/{id}/lock")
    public AccountResponse lock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                @Valid @RequestBody LockRequest request) {
        return service.setLocked(UUID.fromString(jwt.getSubject()), id, request);
    }
}
