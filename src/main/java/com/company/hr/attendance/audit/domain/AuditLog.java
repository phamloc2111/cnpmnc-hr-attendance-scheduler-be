package com.company.hr.attendance.audit.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    private UUID id;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "target_employee_id")
    private UUID targetEmployeeId;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditLog() {}

    public AuditLog(
        UUID id,
        UUID actorId,
        UUID targetEmployeeId,
        LocalDate targetDate,
        String category,
        String action,
        String summary,
        String oldValue,
        String newValue,
        Instant occurredAt
    ) {
        this.id = id;
        this.actorId = actorId;
        this.targetEmployeeId = targetEmployeeId;
        this.targetDate = targetDate;
        this.category = category;
        this.action = action;
        this.summary = summary;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public UUID getActorId() { return actorId; }
    public UUID getTargetEmployeeId() { return targetEmployeeId; }
    public LocalDate getTargetDate() { return targetDate; }
    public String getCategory() { return category; }
    public String getAction() { return action; }
    public String getSummary() { return summary; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public Instant getOccurredAt() { return occurredAt; }
}
