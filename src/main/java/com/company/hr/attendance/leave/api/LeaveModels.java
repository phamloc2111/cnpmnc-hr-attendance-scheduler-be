package com.company.hr.attendance.leave.api;

import com.company.hr.attendance.leave.domain.LeaveRequest;
import com.company.hr.attendance.leave.domain.LeaveStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class LeaveModels {

    public record CreateLeaveRequest(
        UUID scheduleId,
        @NotNull(message = "Ngày làm việc không được trống")
        LocalDate workDate,
        UUID shiftTemplateId,
        @NotBlank(message = "Lý do xin nghỉ không được trống")
        @Size(max = 500, message = "Lý do không được quá 500 ký tự")
        String reason
    ) {}

    public record ReviewLeaveRequest(
        @NotBlank(message = "Hành động duyệt không được trống")
        String action, // APPROVE, REJECT
        @Size(max = 500, message = "Ghi chú không được quá 500 ký tự")
        String note
    ) {}

    public record LeaveQuotaResponse(
        String month,
        int maxAllowed,
        int approved,
        int pending,
        int remaining,
        boolean canSubmit
    ) {}

    public record LeaveResponse(
        UUID id,
        UUID employeeId,
        String employeeName,
        UUID scheduleId,
        LocalDate workDate,
        UUID shiftTemplateId,
        String shiftName,
        String reason,
        LeaveStatus status,
        UUID reviewerId,
        String reviewerName,
        String reviewerNote,
        Instant reviewedAt,
        Instant createdAt
    ) {
        public static LeaveResponse from(LeaveRequest req, String employeeName, String reviewerName) {
            return new LeaveResponse(
                req.getId(),
                req.getEmployeeId(),
                employeeName,
                req.getScheduleId(),
                req.getWorkDate(),
                req.getShiftTemplateId(),
                req.getShiftName(),
                req.getReason(),
                req.getStatus(),
                req.getReviewerId(),
                reviewerName,
                req.getReviewerNote(),
                req.getReviewedAt(),
                req.getCreatedAt()
            );
        }
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
        public static <T> PageResponse<T> from(Page<T> result) {
            return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        }
    }
}
