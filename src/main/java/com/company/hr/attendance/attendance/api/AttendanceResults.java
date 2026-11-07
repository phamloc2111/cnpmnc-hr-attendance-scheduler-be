package com.company.hr.attendance.attendance.api;

import com.company.hr.attendance.attendance.domain.AttendanceClassifier.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AttendanceResults {
    private AttendanceResults() {}
    public record ResultResponse(UUID scheduleId, UUID employeeId, String employeeName, LocalDate workDate,
        String shiftName, Instant startsAt, Instant endsAt, Instant checkInAt, Instant checkOutAt,
        Arrival arrival, Departure departure, Overall overall, long lateMinutes, long earlyMinutes) {}
    public record ResultPage(List<ResultResponse> items, int page, int size, long totalElements, int totalPages) {}
}
