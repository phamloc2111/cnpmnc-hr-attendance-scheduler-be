package com.company.hr.attendance.workschedule.api;

import com.company.hr.attendance.workschedule.domain.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.Page;

public final class ScheduleModels {
    private ScheduleModels() {}
    public record CreateShift(
        @NotBlank @Size(max = 120) String name,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Min(0) @Max(1439) int breakMinutes,
        @NotNull @Min(1) @JsonDeserialize(using = HeadcountDeserializer.class) Integer requiredHeadcount,
        @NotNull UUID requiredPositionId,
        @NotNull Boolean overnight) {}
    public static class HeadcountDeserializer extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                throw JsonMappingException.from(parser, "Số người cần phải là số nguyên.");
            }
            return parser.getIntValue();
        }
    }
    public record AssignShift(@NotNull UUID employeeId, @NotNull UUID shiftTemplateId, @NotNull LocalDate workDate) {}
    public record GenerateSchedule(@NotNull LocalDate weekStart, UUID managerId,
        @NotEmpty @Size(max = 20) List<@NotNull UUID> shiftTemplateIds,
        @NotEmpty @Size(max = 7) List<@NotNull LocalDate> workDates) {}
    public record GenerationResponse(LocalDate weekStart, int createdCount, long maxWeeklyMinutes,
        long minWeeklyMinutes, int belowMinimumEmployeeCount, List<ScheduleResponse> items) {}
    public record ShiftResponse(UUID id, String name, LocalTime startTime, LocalTime endTime,
                                int breakMinutes, int workingMinutes, boolean overnight,
                                int requiredHeadcount, String requiredPosition, UUID requiredPositionId) {
        public static ShiftResponse from(ShiftTemplate shift) {
            return new ShiftResponse(shift.getId(), shift.getName(), shift.getStartTime(), shift.getEndTime(),
                shift.getBreakMinutes(), ShiftWindow.durationMinutes(shift.getStartTime(), shift.getEndTime()) - shift.getBreakMinutes(),
                shift.isOvernight(), shift.getRequiredHeadcount(), shift.getRequiredPosition(), shift.getRequiredPositionId());
        }
    }
    public record ScheduleResponse(UUID id, UUID employeeId, String employeeName, UUID shiftTemplateId,
        LocalDate workDate, String shiftName, Instant startsAt, Instant endsAt, int breakMinutes,
        long workingMinutes, String timezone, String requiredPosition) {
        public static ScheduleResponse from(WorkSchedule schedule, String name, String requiredPosition) {
            return new ScheduleResponse(schedule.getId(), schedule.getEmployeeId(), name, schedule.getShiftTemplateId(),
                schedule.getWorkDate(), schedule.getShiftName(), schedule.getStartsAt(), schedule.getEndsAt(),
                schedule.getBreakMinutes(), Duration.between(schedule.getStartsAt(), schedule.getEndsAt()).toMinutes()
                    - schedule.getBreakMinutes(), schedule.getTimezone(), requiredPosition);
        }
    }
    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
        public static <T> PageResponse<T> from(Page<T> result) {
            return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        }
    }
}
