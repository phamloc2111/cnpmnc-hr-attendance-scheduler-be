package com.company.hr.attendance.organization.api;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class WorkplaceModels {
    private WorkplaceModels() {}
    public record SaveWorkplace(
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @NotNull @Min(10) @Max(5000) Integer radiusMeters,
        @NotNull @Min(20) @Max(50) Integer gpsToleranceMeters,
        @NotNull @Min(0) Long version) {}
    public record WorkplaceResponse(boolean configured, Double latitude, Double longitude, int radiusMeters,
        int gpsToleranceMeters, long version, Instant updatedAt, UUID updatedBy) {}
}
