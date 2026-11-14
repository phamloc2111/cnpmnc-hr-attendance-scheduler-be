package com.company.hr.attendance.shiftpass.infrastructure;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ShiftPassAuditRecorder {
    private final JdbcTemplate jdbc;

    public ShiftPassAuditRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void recordClaim(
        UUID actorId,
        UUID targetEmployeeId,
        LocalDate targetDate,
        String summary,
        String oldValue,
        String newValue,
        Instant occurredAt
    ) {
        jdbc.update(
            "INSERT INTO audit_logs (id, actor_id, target_employee_id, target_date, category, action, summary, old_value, new_value, occurred_at) " +
                "VALUES (?, ?, ?, ?, 'SCHEDULE', 'SHIFT_PASS_CLAIMED', ?, ?, ?, ?)",
            UUID.randomUUID(),
            actorId,
            targetEmployeeId,
            targetDate,
            summary,
            oldValue,
            newValue,
            OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC)
        );
    }
}
