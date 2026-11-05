package com.company.hr.attendance.workschedule.domain;

import jakarta.persistence.*;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "work_shift_templates")
public class ShiftTemplate {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false) private LocalTime startTime;
    @Column(nullable = false) private LocalTime endTime;
    @Column(nullable = false) private int breakMinutes;
    @Column(nullable = false) private UUID createdBy;
    protected ShiftTemplate() {}
    public ShiftTemplate(String name, LocalTime start, LocalTime end, int breakMinutes, UUID creator) {
        this.id = UUID.randomUUID(); this.name = name; this.startTime = start; this.endTime = end;
        this.breakMinutes = breakMinutes; this.createdBy = creator;
    }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public int getBreakMinutes() { return breakMinutes; }
}
