package com.company.hr.attendance.workschedule.application;

import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.workschedule.WorkScheduleApi;
import com.company.hr.attendance.workschedule.domain.WorkSchedule;
import com.company.hr.attendance.workschedule.infrastructure.WorkScheduleRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class WorkScheduleDirectory implements WorkScheduleApi {
    private final WorkScheduleRepository schedules;
    private final IdentityApi identity;
    public WorkScheduleDirectory(WorkScheduleRepository schedules, IdentityApi identity) {
        this.schedules = schedules; this.identity = identity;
    }
    @Override
    public java.util.Optional<Shift> lock(UUID id) {
        return schedules.lockById(id).map(this::view);
    }
    @Override
    public Page<Shift> pageForManager(UUID actorId, LocalDate from, LocalDate to, UUID employeeId, int page, int size) {
        var actor = identity.person(actorId);
        if (actor.locked() || !java.util.Set.of("MANAGER", "ADMIN").contains(actor.role()))
            throw new AccessDeniedException("Management access required");
        Specification<WorkSchedule> filter = (root, ignored, cb) -> cb.between(root.get("workDate"), from, to);
        if (actor.role().equals("MANAGER")) {
            var ids = identity.managedEmployeeIds(actorId);
            if (employeeId != null && !ids.contains(employeeId)) throw new AccessDeniedException("Employee unavailable");
            filter = filter.and((root, ignored, cb) -> ids.isEmpty() ? cb.disjunction() : root.get("employeeId").in(ids));
        }
        if (employeeId != null) filter = filter.and((root, ignored, cb) -> cb.equal(root.get("employeeId"), employeeId));
        return schedules.findAll(filter, PageRequest.of(page, size, Sort.by("workDate", "startsAt", "id"))).map(this::view);
    }
    @Override
    public java.util.List<Shift> employeeShifts(java.util.Collection<UUID> employeeIds, LocalDate from, LocalDate to) {
        if (employeeIds.isEmpty()) return java.util.List.of();
        Specification<WorkSchedule> filter = (root, ignored, cb) -> cb.and(
            root.get("employeeId").in(employeeIds), cb.between(root.get("workDate"), from, to));
        return schedules.findAll(filter, Sort.by("workDate", "startsAt", "id")).stream().map(this::view).toList();
    }
    private Shift view(WorkSchedule schedule) {
        return new Shift(schedule.getId(), schedule.getEmployeeId(), schedule.getWorkDate(),
            schedule.getShiftName(), schedule.getStartsAt(), schedule.getEndsAt(), schedule.getBreakMinutes());
    }
}
