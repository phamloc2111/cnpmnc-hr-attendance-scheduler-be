package com.company.hr.attendance.workschedule.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "schedule_draft_weeks", uniqueConstraints =
    @UniqueConstraint(name = "uq_schedule_draft_week", columnNames = {"manager_id", "week_start"}))
public class ScheduleDraftWeek {
    @Id
    private UUID id;
    @Column(name = "manager_id", nullable = false)
    private UUID managerId;
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    protected ScheduleDraftWeek() {}

    public ScheduleDraftWeek(UUID managerId, LocalDate weekStart) {
        this.id = UUID.randomUUID();
        this.managerId = managerId;
        this.weekStart = weekStart;
    }
}
