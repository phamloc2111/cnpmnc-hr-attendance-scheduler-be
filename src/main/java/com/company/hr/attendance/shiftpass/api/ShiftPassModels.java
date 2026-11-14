package com.company.hr.attendance.shiftpass.api;

import com.company.hr.attendance.shiftpass.domain.ShiftPass;
import com.company.hr.attendance.shiftpass.domain.ShiftPassNotification;
import com.company.hr.attendance.shiftpass.domain.ShiftPassStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class ShiftPassModels {
    private ShiftPassModels() {}

    public record CreateShiftPassRequest(
        @NotNull UUID scheduleId,
        @Size(max = 500) String reason
    ) {}

    public record ShiftPassResponse(
        UUID id,
        UUID scheduleId,
        UUID requesterId,
        String requesterName,
        UUID claimerId,
        String claimerName,
        LocalDate workDate,
        String shiftName,
        Instant startsAt,
        Instant endsAt,
        int breakMinutes,
        long workingMinutes,
        UUID requiredPositionId,
        String requiredPosition,
        String reason,
        ShiftPassStatus status,
        Instant createdAt,
        Instant claimedAt,
        int notifiedCount
    ) {
        public static ShiftPassResponse from(ShiftPass pass, String requesterName, String claimerName, int notifiedCount) {
            long workingMinutes = Math.max(0, Duration.between(pass.getStartsAt(), pass.getEndsAt()).toMinutes() - pass.getBreakMinutes());
            return new ShiftPassResponse(
                pass.getId(),
                pass.getScheduleId(),
                pass.getRequesterId(),
                requesterName,
                pass.getClaimerId(),
                claimerName,
                pass.getWorkDate(),
                pass.getShiftName(),
                pass.getStartsAt(),
                pass.getEndsAt(),
                pass.getBreakMinutes(),
                workingMinutes,
                pass.getRequiredPositionId(),
                pass.getRequiredPosition(),
                pass.getReason(),
                pass.getStatus(),
                pass.getCreatedAt(),
                pass.getClaimedAt(),
                notifiedCount
            );
        }
    }

    public record AvailableShiftPassResponse(
        UUID id,
        UUID scheduleId,
        UUID requesterId,
        String requesterName,
        LocalDate workDate,
        String shiftName,
        Instant startsAt,
        Instant endsAt,
        int breakMinutes,
        long workingMinutes,
        UUID requiredPositionId,
        String requiredPosition,
        String reason,
        Instant createdAt,
        boolean eligible,
        String ineligibleReason,
        long currentWeekMinutes,
        long maxWeeklyMinutes
    ) {
        public static AvailableShiftPassResponse from(
            ShiftPass pass,
            String requesterName,
            long workingMinutes,
            boolean eligible,
            String ineligibleReason,
            long currentWeekMinutes,
            long maxWeeklyMinutes
        ) {
            return new AvailableShiftPassResponse(
                pass.getId(),
                pass.getScheduleId(),
                pass.getRequesterId(),
                requesterName,
                pass.getWorkDate(),
                pass.getShiftName(),
                pass.getStartsAt(),
                pass.getEndsAt(),
                pass.getBreakMinutes(),
                workingMinutes,
                pass.getRequiredPositionId(),
                pass.getRequiredPosition(),
                pass.getReason(),
                pass.getCreatedAt(),
                eligible,
                ineligibleReason,
                currentWeekMinutes,
                maxWeeklyMinutes
            );
        }
    }

    public record ShiftPassNotificationResponse(
        UUID id,
        UUID shiftPassId,
        String kind,
        String title,
        String body,
        String status,
        Instant createdAt,
        Instant closedAt
    ) {
        public static ShiftPassNotificationResponse from(ShiftPassNotification n) {
            return new ShiftPassNotificationResponse(
                n.getId(),
                n.getShiftPassId(),
                n.getKind(),
                n.getTitle(),
                n.getBody(),
                n.getStatus(),
                n.getCreatedAt(),
                n.getClosedAt()
            );
        }
    }
}
