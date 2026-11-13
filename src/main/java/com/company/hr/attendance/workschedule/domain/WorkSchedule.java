package com.company.hr.attendance.workschedule.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "work_schedules")
public class WorkSchedule {
    @Id private UUID id;
    @Column(nullable = false) private UUID employeeId;
    @Column(nullable = false) private UUID shiftTemplateId;
    @Column(nullable = false) private LocalDate workDate;
    @Column(nullable = false, length = 120) private String shiftName;
    @Column(nullable = false) private Instant startsAt;
    @Column(nullable = false) private Instant endsAt;
    @Column(nullable = false) private int breakMinutes;
    @Column(nullable = false, length = 64) private String timezone;
    @Column(nullable = false) private UUID createdBy;
    protected WorkSchedule() {}
    public WorkSchedule(UUID employeeId, ShiftTemplate shift, LocalDate date, ZoneId zone, UUID creator) {
        this.id = UUID.randomUUID(); this.employeeId = employeeId; this.shiftTemplateId = shift.getId();
        this.workDate = date; this.shiftName = shift.getName(); this.breakMinutes = shift.getBreakMinutes();
        this.timezone = zone.getId(); this.createdBy = creator;
        var window = ShiftWindow.of(date, shift.getStartTime(), shift.getEndTime(), zone);
        this.startsAt = window.startsAt(); this.endsAt = window.endsAt();
    }
    public UUID getId() { return id; }
    public UUID getEmployeeId() { return employeeId; }
    public UUID getShiftTemplateId() { return shiftTemplateId; }
    public LocalDate getWorkDate() { return workDate; }
    public String getShiftName() { return shiftName; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public int getBreakMinutes() { return breakMinutes; }
    public String getTimezone() { return timezone; }
    public void reassignEmployee(UUID employeeId) { this.employeeId = employeeId; }
}
