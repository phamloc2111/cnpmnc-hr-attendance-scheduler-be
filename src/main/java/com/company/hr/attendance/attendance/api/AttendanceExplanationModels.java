package com.company.hr.attendance.attendance.api;

import com.company.hr.attendance.attendance.domain.AttendanceExplanation;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class AttendanceExplanationModels {
    private AttendanceExplanationModels() {}
    public record SubmitRequest(@NotNull UUID scheduleId, @NotNull AttendanceExplanation.Category category,
        UUID attendanceRecordId, @NotBlank @Size(min=10,max=2000) String reason) {}
    public record DecisionRequest(boolean approved, @Size(max=2000) String reason, boolean exemptLatePenalty) {}
    public record ExplanationResponse(UUID id,UUID scheduleId,UUID employeeId,String employeeName,LocalDate workDate,
        AttendanceExplanation.Category category,UUID attendanceRecordId,String reason,Instant submittedAt,
        AttendanceExplanation.Status status,String decisionReason,boolean exemptLatePenalty,UUID decidedBy,Instant decidedAt) {
        public static ExplanationResponse from(AttendanceExplanation row,String name){return new ExplanationResponse(row.id(),row.scheduleId(),row.employeeId(),name,row.workDate(),row.category(),row.attendanceRecordId(),row.reason(),row.submittedAt(),row.status(),row.decisionReason(),row.exemptLatePenalty(),row.decidedBy(),row.decidedAt());}
    }
    public record Candidate(UUID scheduleId,LocalDate workDate,String shiftName,Instant startsAt,Instant endsAt,
        AttendanceExplanation.Category category,UUID attendanceRecordId,ExplanationResponse explanation) {}
    public record Page<T>(List<T> items,int page,int size,long totalElements,int totalPages) {}
}
