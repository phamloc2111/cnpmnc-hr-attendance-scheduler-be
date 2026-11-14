package com.company.hr.attendance.workschedule.application;

import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.workschedule.WorkScheduleApi;
import com.company.hr.attendance.workschedule.domain.ShiftTemplate;
import com.company.hr.attendance.workschedule.domain.WorkSchedule;
import com.company.hr.attendance.workschedule.infrastructure.ShiftTemplateRepository;
import com.company.hr.attendance.workschedule.infrastructure.WorkScheduleRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkScheduleDirectory implements WorkScheduleApi {
    private final WorkScheduleRepository schedules;
    private final IdentityApi identity;
    private final ShiftTemplateRepository templates;
    public WorkScheduleDirectory(WorkScheduleRepository schedules, IdentityApi identity) {
        this(schedules, identity, null);
    }
    @Autowired
    public WorkScheduleDirectory(WorkScheduleRepository schedules, IdentityApi identity, ShiftTemplateRepository templates) {
        this.schedules = schedules; this.identity = identity; this.templates = templates;
    }
    @Override
    public java.util.Optional<Shift> lock(UUID id) {
        return schedules.lockById(id).filter(schedule -> !schedule.isDraft()).map(this::view);
    }
    @Override
    public Optional<ShiftDetail> findDetail(UUID id) {
        return schedules.findById(id).map(schedule -> {
            Optional<ShiftTemplate> template = templates == null ? Optional.empty() : templates.findById(schedule.getShiftTemplateId());
            UUID posId = template.map(ShiftTemplate::getRequiredPositionId).orElse(null);
            String posName = template.map(ShiftTemplate::getRequiredPosition).orElse(null);
            return new ShiftDetail(schedule.getId(), schedule.getEmployeeId(), schedule.getShiftTemplateId(),
                schedule.getWorkDate(), schedule.getShiftName(), schedule.getStartsAt(), schedule.getEndsAt(),
                schedule.getBreakMinutes(), posId, posName);
        });
    }
    @Override
    @Transactional
    public boolean transferShift(UUID scheduleId, UUID expectedOwnerId, UUID newEmployeeId) {
        var locked = schedules.lockById(scheduleId);
        if (locked.isEmpty()) return false;
        var schedule = locked.get();
        if (!schedule.getEmployeeId().equals(expectedOwnerId)) return false;
        if (schedules.existsByEmployeeIdAndStartsAtLessThanAndEndsAtGreaterThan(
            newEmployeeId, schedule.getEndsAt(), schedule.getStartsAt())) {
            return false;
        }
        schedule.reassignEmployee(newEmployeeId);
        schedules.saveAndFlush(schedule);
        return true;
    }
    @Override
    public Page<Shift> pageForManager(UUID actorId, LocalDate from, LocalDate to, UUID employeeId, int page, int size) {
        var actor = identity.person(actorId);
        if (actor.locked() || !java.util.Set.of("MANAGER", "ADMIN").contains(actor.role()))
            throw new AccessDeniedException("Management access required");
        Specification<WorkSchedule> filter = (root, ignored, cb) -> cb.and(
            cb.between(root.get("workDate"), from, to), cb.isFalse(root.get("draft")));
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
            root.get("employeeId").in(employeeIds), cb.between(root.get("workDate"), from, to),
            cb.isFalse(root.get("draft")));
        return schedules.findAll(filter, Sort.by("workDate", "startsAt", "id")).stream().map(this::view).toList();
    }
    private Shift view(WorkSchedule schedule) {
        return new Shift(schedule.getId(), schedule.getEmployeeId(), schedule.getWorkDate(),
            schedule.getShiftName(), schedule.getStartsAt(), schedule.getEndsAt(), schedule.getBreakMinutes());
    }
}
