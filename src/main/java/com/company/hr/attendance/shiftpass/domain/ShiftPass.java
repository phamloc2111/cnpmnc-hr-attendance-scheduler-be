package com.company.hr.attendance.shiftpass.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "shift_passes")
public class ShiftPass {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID scheduleId;

    @Column(unique = true)
    private UUID openScheduleId;

    @Column(nullable = false)
    private UUID requesterId;

    @Column
    private UUID claimerId;

    @Column(nullable = false)
    private LocalDate workDate;

    @Column(nullable = false, length = 120)
    private String shiftName;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private int breakMinutes;

    @Column
    private UUID requiredPositionId;

    @Column(length = 120)
    private String requiredPosition;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ShiftPassStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant claimedAt;

    @Column
    private Instant closedAt;

    @Version
    private long version;

    protected ShiftPass() {}

    public ShiftPass(
        UUID scheduleId,
        UUID requesterId,
        LocalDate workDate,
        String shiftName,
        Instant startsAt,
        Instant endsAt,
        int breakMinutes,
        UUID requiredPositionId,
        String requiredPosition,
        String reason,
        Instant now
    ) {
        this.id = UUID.randomUUID();
        this.scheduleId = scheduleId;
        this.openScheduleId = scheduleId;
        this.requesterId = requesterId;
        this.workDate = workDate;
        this.shiftName = shiftName;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.breakMinutes = breakMinutes;
        this.requiredPositionId = requiredPositionId;
        this.requiredPosition = requiredPosition;
        this.reason = reason == null || reason.isBlank() ? null : reason.strip();
        this.status = ShiftPassStatus.OPEN;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getScheduleId() { return scheduleId; }
    public UUID getOpenScheduleId() { return openScheduleId; }
    public UUID getRequesterId() { return requesterId; }
    public UUID getClaimerId() { return claimerId; }
    public LocalDate getWorkDate() { return workDate; }
    public String getShiftName() { return shiftName; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public int getBreakMinutes() { return breakMinutes; }
    public UUID getRequiredPositionId() { return requiredPositionId; }
    public String getRequiredPosition() { return requiredPosition; }
    public String getReason() { return reason; }
    public ShiftPassStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getClaimedAt() { return claimedAt; }
    public Instant getClosedAt() { return closedAt; }

    public void cancel(Instant now) {
        this.status = ShiftPassStatus.CANCELLED;
        this.openScheduleId = null;
        this.closedAt = now;
    }

    public void expire(Instant now) {
        this.status = ShiftPassStatus.EXPIRED;
        this.openScheduleId = null;
        this.closedAt = now;
    }

    public void markClaimed(UUID claimerId, Instant now) {
        this.status = ShiftPassStatus.CLAIMED;
        this.openScheduleId = null;
        this.claimerId = claimerId;
        this.claimedAt = now;
        this.closedAt = now;
    }
}
