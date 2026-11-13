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
