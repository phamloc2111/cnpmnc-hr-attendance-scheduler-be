package com.company.hr.attendance.workschedule;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;

/** Schedule data exposed to attendance without sharing persistence entities. */
public interface WorkScheduleApi {
    record Shift(UUID id, UUID employeeId, LocalDate workDate, String name, Instant startsAt, Instant endsAt, int breakMinutes) {
        public Shift(UUID id, UUID employeeId, LocalDate workDate, String name, Instant startsAt, Instant endsAt) {
            this(id,employeeId,workDate,name,startsAt,endsAt,0);
        }
    }
    java.util.List<Shift> employeeShifts(java.util.Collection<UUID> employeeIds, LocalDate from, LocalDate to);
    record ShiftDetail(UUID id, UUID employeeId, UUID shiftTemplateId, LocalDate workDate, String name,
        Instant startsAt, Instant endsAt, int breakMinutes, UUID requiredPositionId, String requiredPosition) {}
    Optional<Shift> lock(UUID id);
    Optional<ShiftDetail> findDetail(UUID id);
    boolean transferShift(UUID scheduleId, UUID expectedOwnerId, UUID newEmployeeId);
    Page<Shift> pageForManager(UUID actorId, LocalDate from, LocalDate to, UUID employeeId, int page, int size);
}
