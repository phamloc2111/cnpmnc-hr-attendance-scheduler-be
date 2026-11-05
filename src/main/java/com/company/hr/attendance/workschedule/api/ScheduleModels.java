package com.company.hr.attendance.workschedule.api;

import com.company.hr.attendance.workschedule.domain.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.Page;

public final class ScheduleModels {
    private ScheduleModels() {}
    public record CreateShift(
        @NotBlank @Size(max = 120) String name,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Min(0) @Max(1439) int breakMinutes) {}
    public record AssignShift(@NotNull UUID employeeId, @NotNull UUID shiftTemplateId, @NotNull LocalDate workDate) {}
    public record ShiftResponse(UUID id, String name, LocalTime startTime, LocalTime endTime,
                                int breakMinutes, int workingMinutes, boolean overnight) {
        public static ShiftResponse from(ShiftTemplate shift) {
            return new ShiftResponse(shift.getId(), shift.getName(), shift.getStartTime(), shift.getEndTime(),
                shift.getBreakMinutes(), ShiftWindow.durationMinutes(shift.getStartTime(), shift.getEndTime()) - shift.getBreakMinutes(),
                shift.getEndTime().isBefore(shift.getStartTime()));
        }
    }
    public record ScheduleResponse(UUID id, UUID employeeId, String employeeName, UUID shiftTemplateId,
        LocalDate workDate, String shiftName, Instant startsAt, Instant endsAt, int breakMinutes,
        long workingMinutes, String timezone) {
        public static ScheduleResponse from(WorkSchedule schedule, String name) {
            return new ScheduleResponse(schedule.getId(), schedule.getEmployeeId(), name, schedule.getShiftTemplateId(),
                schedule.getWorkDate(), schedule.getShiftName(), schedule.getStartsAt(), schedule.getEndsAt(),
                schedule.getBreakMinutes(), Duration.between(schedule.getStartsAt(), schedule.getEndsAt()).toMinutes()
                    - schedule.getBreakMinutes(), schedule.getTimezone());
        }
    }
    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
        public static <T> PageResponse<T> from(Page<T> result) {
            return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        }
    }
}
