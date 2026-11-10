package com.company.hr.attendance.workschedule;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;

/** Schedule data exposed to attendance without sharing persistence entities. */
public interface WorkScheduleApi {
    record Shift(UUID id, UUID employeeId, LocalDate workDate, String name, Instant startsAt, Instant endsAt) {}
    java.util.List<Shift> employeeShifts(java.util.Collection<UUID> employeeIds, LocalDate from, LocalDate to);
    Optional<Shift> lock(UUID id);
    Page<Shift> pageForManager(UUID actorId, LocalDate from, LocalDate to, UUID employeeId, int page, int size);
}
