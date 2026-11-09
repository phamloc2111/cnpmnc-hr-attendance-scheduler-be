package com.company.hr.attendance.organization;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Public boundary for the shared attendance workplace configuration. */
public interface WorkplaceApi {
    record Configuration(double latitude, double longitude, int radiusMeters, int gpsToleranceMeters,
                         long version, Instant updatedAt, UUID updatedBy) {}
    Optional<Configuration> current();
}
