package com.company.hr.attendance.audit;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Public boundary for recording read-only audit trail entries of schedule and attendance changes. */
public interface AuditApi {
    void record(
        UUID actorId,
        UUID targetEmployeeId,
        LocalDate targetDate,
        String category,
        String action,
        String summary,
        String oldValue,
        String newValue,
        Instant occurredAt
    );
}
