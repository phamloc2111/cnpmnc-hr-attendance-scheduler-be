package com.company.hr.attendance.workschedule.application;

import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.organization.PositionsApi;
import com.company.hr.attendance.workschedule.api.ScheduleModels.DraftOvertime;
import com.company.hr.attendance.workschedule.api.ScheduleModels.DraftShortage;
import com.company.hr.attendance.workschedule.api.ScheduleModels.DraftWarningsResponse;
import com.company.hr.attendance.workschedule.api.ScheduleModels.DraftSlot;
import com.company.hr.attendance.workschedule.domain.ShiftTemplate;
import com.company.hr.attendance.workschedule.domain.WorkSchedule;
import com.company.hr.attendance.workschedule.infrastructure.ScheduleDraftWeekRepository;
import com.company.hr.attendance.workschedule.infrastructure.ScheduleDraftSlotRepository;
import com.company.hr.attendance.workschedule.infrastructure.ShiftTemplateRepository;
import com.company.hr.attendance.workschedule.infrastructure.WorkScheduleRepository;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ScheduleDraftWarningsService {
    private final IdentityApi identity;
    private final PositionsApi positions;
    private final ShiftTemplateRepository templates;
    private final WorkScheduleRepository schedules;
    private final ScheduleDraftWeekRepository draftWeeks;
    private final ScheduleDraftSlotRepository draftSlots;
    private final long maxWeeklyMinutes;

    public ScheduleDraftWarningsService(IdentityApi identity, PositionsApi positions, ShiftTemplateRepository templates,
        WorkScheduleRepository schedules, ScheduleDraftWeekRepository draftWeeks, ScheduleDraftSlotRepository draftSlots,
        @Value("${app.scheduling.max-weekly-minutes:0}") long maxWeeklyMinutes) {
        this.identity = identity;
        this.positions = positions;
        this.templates = templates;
        this.schedules = schedules;
        this.draftWeeks = draftWeeks;
        this.draftSlots = draftSlots;
        this.maxWeeklyMinutes = maxWeeklyMinutes;
    }

    public DraftWarningsResponse warnings(UUID actorId, LocalDate weekStart, UUID requestedManagerId) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new ScheduleException(HttpStatus.BAD_REQUEST, "INVALID_GENERATION_REQUEST",
                "Tuần xem cảnh báo phải bắt đầu vào thứ Hai.");
        }
        var actor = identity.person(actorId);
        if (actor.locked() || !Set.of("MANAGER", "ADMIN").contains(actor.role())) throw forbidden();
        UUID managerId = actor.role().equals("MANAGER") ? actorId : requestedManagerId;
        if (managerId == null || actor.role().equals("MANAGER") && requestedManagerId != null
            && !requestedManagerId.equals(actorId)) throw forbidden();
        var manager = identity.person(managerId);
        if (manager.locked() || !manager.role().equals("MANAGER")) throw forbidden();
        if (!draftWeeks.existsByManagerIdAndWeekStart(managerId, weekStart)) {
            return new DraftWarningsResponse(weekStart, false, maxWeeklyMinutes, 0, List.of(), List.of());
        }

        var ids = identity.managedEmployeeIds(managerId);
        var rows = ids.isEmpty() ? List.<WorkSchedule>of()
            : schedules.findByEmployeeIdInAndWorkDateBetween(ids, weekStart, weekStart.plusDays(6));
        var filled = new HashMap<ShiftDate, Integer>();
        var minutes = new HashMap<UUID, Long>();
        for (var row : rows) {
            filled.merge(new ShiftDate(row.getShiftTemplateId(), row.getWorkDate()), 1, Integer::sum);
            minutes.merge(row.getEmployeeId(), Math.max(0,
                Duration.between(row.getStartsAt(), row.getEndsAt()).toMinutes() - row.getBreakMinutes()), Long::sum);
        }
        var shortages = new ArrayList<DraftShortage>();
        var allTemplates = templates.findAll();
        var activePositions = positions.activeIds(allTemplates.stream().map(ShiftTemplate::getRequiredPositionId)
            .filter(java.util.Objects::nonNull).distinct().toList());
        var selected = allTemplates.stream()
            .filter(template -> template.getRequiredPositionId() != null
                && activePositions.contains(template.getRequiredPositionId())
                && template.getRequiredHeadcount() > 0)
            .sorted(Comparator.comparing(ShiftTemplate::getName).thenComparing(ShiftTemplate::getId)).toList();
        var selectedById = new HashMap<UUID, ShiftTemplate>();
        selected.forEach(template -> selectedById.put(template.getId(), template));
        var savedSlots = draftSlots.find(managerId, weekStart);
        for (var slot : savedSlots) {
            var template = selectedById.get(slot.shiftTemplateId());
            if (template == null) continue;
            int missing = Math.max(0, template.getRequiredHeadcount()
                - filled.getOrDefault(new ShiftDate(template.getId(), slot.workDate()), 0));
            if (missing > 0) shortages.add(new DraftShortage(slot.workDate(), template.getId(), template.getName(), missing));
        }
        var names = identity.names(minutes.keySet());
        var overtime = maxWeeklyMinutes <= 0 ? List.<DraftOvertime>of() : minutes.entrySet().stream()
            .filter(entry -> entry.getValue() > maxWeeklyMinutes)
            .map(entry -> new DraftOvertime(entry.getKey(), names.getOrDefault(entry.getKey(), "Nhân viên"),
                entry.getValue(), entry.getValue() - maxWeeklyMinutes))
            .sorted(Comparator.comparing(DraftOvertime::employeeName).thenComparing(DraftOvertime::employeeId))
            .toList();
        int missingCount = shortages.stream().mapToInt(DraftShortage::missingCount).sum();
        return new DraftWarningsResponse(weekStart, true, maxWeeklyMinutes, missingCount, shortages, overtime);
    }

    private record ShiftDate(UUID templateId, LocalDate date) {}

    private ScheduleException forbidden() {
        return new ScheduleException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền xem bản nháp của nhóm này.");
    }
}
