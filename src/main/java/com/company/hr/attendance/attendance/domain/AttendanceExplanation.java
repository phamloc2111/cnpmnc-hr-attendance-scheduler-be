package com.company.hr.attendance.attendance.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AttendanceExplanation(UUID id, UUID scheduleId, UUID employeeId, LocalDate workDate,
    Category category, UUID attendanceRecordId, String reason, Instant submittedAt, Status status,
    String decisionReason, boolean exemptLatePenalty, UUID decidedBy, Instant decidedAt) {
    public enum Category { LATE, MISSING_CHECK_IN, MISSING_CHECK_OUT, INVALID }
    public enum Status { PENDING, APPROVED, REJECTED }
}
