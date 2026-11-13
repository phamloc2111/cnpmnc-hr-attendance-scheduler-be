package com.company.hr.attendance.audit.api;

import com.company.hr.attendance.audit.domain.AuditLog;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public final class AuditModels {
    private AuditModels() {}

    public record AuditLogResponse(
        UUID id,
        UUID actorId,
        String actorName,
        UUID targetEmployeeId,
        String targetEmployeeName,
        LocalDate targetDate,
        String category,
        String action,
        String summary,
        String oldValue,
        String newValue,
        Instant occurredAt
    ) {
        public static AuditLogResponse from(AuditLog log, String actorName, String targetEmployeeName) {
            return new AuditLogResponse(
                log.getId(),
                log.getActorId(),
                actorName,
                log.getTargetEmployeeId(),
                targetEmployeeName,
                log.getTargetDate(),
                log.getCategory(),
                log.getAction(),
                log.getSummary(),
                log.getOldValue(),
                log.getNewValue(),
                log.getOccurredAt()
            );
        }
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
        public static <T> PageResponse<T> from(Page<T> result) {
            return new PageResponse<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
            );
        }
    }
}
