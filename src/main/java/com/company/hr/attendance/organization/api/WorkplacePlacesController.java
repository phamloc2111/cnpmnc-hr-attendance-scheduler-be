package com.company.hr.attendance.organization.api;

import com.company.hr.attendance.organization.application.WorkplacePlacesService;
import com.company.hr.attendance.organization.api.PlaceModels.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workplace-settings/places")
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class WorkplacePlacesController {
    private final WorkplacePlacesService service;
    public WorkplacePlacesController(WorkplacePlacesService service) { this.service = service; }
    @GetMapping public ResponseEntity<List<Suggestion>> search(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "") String q) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.search(UUID.fromString(jwt.getSubject()), q));
    }
    @GetMapping("/detail") public ResponseEntity<Place> detail(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "") String placeId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.detail(UUID.fromString(jwt.getSubject()), placeId));
    }
}
