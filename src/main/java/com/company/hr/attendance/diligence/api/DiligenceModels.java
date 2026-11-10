package com.company.hr.attendance.diligence.api;

import java.time.*;
import java.util.*;

public final class DiligenceModels {
    private DiligenceModels() {}
    public record Policy(int initialScore, int rewardThreshold, int graceMinutes, int under15Penalty, int under30Penalty, int from30Penalty) {}
    public record Summary(UUID employeeId, String employeeName, boolean accountLocked, int score, long deductedPoints,
        long checkInCount, long lateCount, String rewardStatus) {}
    public record Entry(UUID scheduleId, LocalDate workDate, String shiftName, Instant startsAt, Instant checkInAt, long lateSeconds, int deductedPoints) {}
    public record MonthPage(String month, String timezone, boolean finalized, Instant asOf, Policy policy,
        List<Summary> items, int page, int size, long totalElements, int totalPages) {}
    public record EmployeeMonth(String month, String timezone, boolean finalized, Instant asOf, Policy policy,
        Summary employee, List<Entry> items, int page, int size, long totalElements, int totalPages) {}
}
