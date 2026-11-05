package com.company.hr.attendance.workschedule.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "work_shift_templates")
public class ShiftTemplate {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String name;
    @JdbcTypeCode(SqlTypes.LOCAL_TIME)
    @Column(nullable = false) private LocalTime startTime;
    @JdbcTypeCode(SqlTypes.LOCAL_TIME)
    @Column(nullable = false) private LocalTime endTime;
    @Column(nullable = false) private int breakMinutes;
    @Column(nullable = false) private UUID createdBy;
    @Column(nullable = false) private int requiredHeadcount;
    @Column(length = 120) private String requiredPosition;
    @Column private UUID requiredPositionId;
    @Column(nullable = false) private boolean overnight;
    protected ShiftTemplate() {}
    public ShiftTemplate(String name, LocalTime start, LocalTime end, int breakMinutes, UUID creator,
                         int requiredHeadcount, String requiredPosition, UUID requiredPositionId, boolean overnight) {
        this.id = UUID.randomUUID(); this.name = name; this.startTime = start; this.endTime = end;
        this.breakMinutes = breakMinutes; this.createdBy = creator;
        this.requiredHeadcount = requiredHeadcount; this.requiredPosition = requiredPosition; this.requiredPositionId = requiredPositionId; this.overnight = overnight;
    }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public int getBreakMinutes() { return breakMinutes; }
    public int getRequiredHeadcount() { return requiredHeadcount; }
    public String getRequiredPosition() { return requiredPosition; }
    public UUID getRequiredPositionId() { return requiredPositionId; }
    public boolean isOvernight() { return overnight; }
}
