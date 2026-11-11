package com.company.hr.attendance.diligence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Read-only attendance signals for weekly priority scoring. */
public interface DiligenceApi {
    record Signal(UUID employeeId, LocalDate date, boolean unexcusedAbsent, int eligibleShifts, int onTimeShifts) {}
    List<Signal> signals(Collection<UUID> employeeIds, LocalDate from, LocalDate to);
}
