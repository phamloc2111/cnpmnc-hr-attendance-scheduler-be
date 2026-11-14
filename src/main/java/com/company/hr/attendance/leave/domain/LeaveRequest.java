package com.company.hr.attendance.leave.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "leave_requests")
public class LeaveRequest {

    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "schedule_id")
    private UUID scheduleId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "shift_template_id")
    private UUID shiftTemplateId;

    @Column(name = "shift_name", nullable = false)
    private String shiftName;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LeaveStatus status;

    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Column(name = "reviewer_note")
    private String reviewerNote;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LeaveRequest() {}

    public LeaveRequest(UUID id, UUID employeeId, UUID scheduleId, LocalDate workDate,
                        UUID shiftTemplateId, String shiftName, String reason, Instant now) {
        this.id = id;
        this.employeeId = employeeId;
        this.scheduleId = scheduleId;
        this.workDate = workDate;
        this.shiftTemplateId = shiftTemplateId;
        this.shiftName = shiftName;
        this.reason = reason;
        this.status = LeaveStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void approve(UUID reviewerId, String reviewerNote, Instant now) {
        this.status = LeaveStatus.APPROVED;
        this.reviewerId = reviewerId;
        this.reviewerNote = reviewerNote;
        this.reviewedAt = now;
        this.updatedAt = now;
    }

    public void reject(UUID reviewerId, String reviewerNote, Instant now) {
        this.status = LeaveStatus.REJECTED;
        this.reviewerId = reviewerId;
        this.reviewerNote = reviewerNote;
        this.reviewedAt = now;
        this.updatedAt = now;
    }

    public void cancel(Instant now) {
        this.status = LeaveStatus.CANCELLED;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getEmployeeId() { return employeeId; }
    public UUID getScheduleId() { return scheduleId; }
    public LocalDate getWorkDate() { return workDate; }
    public UUID getShiftTemplateId() { return shiftTemplateId; }
    public String getShiftName() { return shiftName; }
    public String getReason() { return reason; }
    public LeaveStatus getStatus() { return status; }
    public UUID getReviewerId() { return reviewerId; }
    public String getReviewerNote() { return reviewerNote; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
