package com.company.hr.attendance.workschedule.application;

import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.identity.IdentityApi.ScoreProfile;
import com.company.hr.attendance.priority.PriorityService;
import com.company.hr.attendance.registration.infrastructure.RegistrationRepository;
import com.company.hr.attendance.workschedule.api.ScheduleModels.GenerateDraft;
import com.company.hr.attendance.workschedule.api.ScheduleModels.DraftGenerationResponse;
import com.company.hr.attendance.workschedule.api.ScheduleModels.DraftShortage;
import com.company.hr.attendance.workschedule.api.ScheduleModels.ScheduleResponse;
import com.company.hr.attendance.workschedule.application.WeeklySchedulePlanner.Existing;
import com.company.hr.attendance.workschedule.application.WeeklySchedulePlanner.Slot;
import com.company.hr.attendance.workschedule.domain.ShiftTemplate;
import com.company.hr.attendance.workschedule.domain.ShiftWindow;
import com.company.hr.attendance.workschedule.domain.WorkSchedule;
import com.company.hr.attendance.workschedule.infrastructure.ShiftTemplateRepository;
import com.company.hr.attendance.workschedule.infrastructure.ScheduleDraftWeekRepository;
import com.company.hr.attendance.workschedule.infrastructure.WorkScheduleRepository;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates private best-effort weekly drafts from registrations and priority scores. */
@Service
public class ScheduleGenerationService {
    private final IdentityApi identity;
    private final ShiftTemplateRepository templates;
    private final WorkScheduleRepository schedules;
    private final ScheduleDraftWeekRepository draftWeeks;
    private final RegistrationRepository registrations;
    private final PriorityService priority;
    private final ZoneId zone;
    private final long maxWeeklyMinutes;
    private final long minWeeklyMinutes;

    public ScheduleGenerationService(IdentityApi identity, ShiftTemplateRepository templates,
        WorkScheduleRepository schedules, ScheduleDraftWeekRepository draftWeeks,
        RegistrationRepository registrations, PriorityService priority,
        @Value("${app.timezone:Asia/Ho_Chi_Minh}") String timezone,
        @Value("${app.scheduling.max-weekly-minutes:0}") long maxWeeklyMinutes,
        @Value("${app.scheduling.min-weekly-minutes:0}") long minWeeklyMinutes) {
        this.identity = identity;
        this.templates = templates;
        this.schedules = schedules;
        this.draftWeeks = draftWeeks;
        this.registrations = registrations;
        this.priority = priority;
        this.zone = ZoneId.of(timezone);
        this.maxWeeklyMinutes = maxWeeklyMinutes;
        this.minWeeklyMinutes = minWeeklyMinutes;
    }

    @Transactional
    public DraftGenerationResponse generateDraft(UUID actorId, GenerateDraft request) {
        var actor = identity.person(actorId);
        if (actor.locked() || !Set.of("MANAGER", "ADMIN").contains(actor.role())) throw forbidden();
        var templateIds = templates.findAll().stream()
            .filter(template -> template.getRequiredPositionId() != null && template.getRequiredHeadcount() > 0)
            .map(ShiftTemplate::getId).toList();
        var dates = IntStream.range(0, 7).mapToObj(request.weekStart()::plusDays).toList();
        var output = plan(actorId, new PlanRequest(request.weekStart(), request.managerId(), templateIds, dates));
        UUID managerId = actor.role().equals("MANAGER") ? actorId : request.managerId();
        draftWeeks.createIfAbsent(UUID.randomUUID(), managerId, request.weekStart());
        var missing = new LinkedHashMap<MissingSlot, Integer>();
        for (var slot : output.unfilled()) {
            missing.merge(new MissingSlot(slot.workDate(), slot.templateId()), 1, Integer::sum);
        }
        var shortages = missing.entrySet().stream().map(entry -> new DraftShortage(entry.getKey().date(),
            entry.getKey().templateId(), output.templates().get(entry.getKey().templateId()).getName(), entry.getValue()))
            .toList();
        return new DraftGenerationResponse(request.weekStart(), output.items().size(),
            output.unfilled().size(), shortages, output.items());
    }

    private record PlanRequest(LocalDate weekStart, UUID managerId, List<UUID> shiftTemplateIds,
        List<LocalDate> workDates) {}
    private record MissingSlot(LocalDate date, UUID templateId) {}
    private record PlanOutput(List<ScheduleResponse> items, List<Slot> unfilled,
        Map<UUID, ShiftTemplate> templates) {}

    private PlanOutput plan(UUID actorId, PlanRequest request) {
        if (maxWeeklyMinutes <= 0 || maxWeeklyMinutes > 10_080 || minWeeklyMinutes < 0 || minWeeklyMinutes > maxWeeklyMinutes) {
            throw new ScheduleException(HttpStatus.SERVICE_UNAVAILABLE, "SCHEDULING_POLICY_NOT_CONFIGURED",
                "Chưa cấu hình giới hạn giờ tuần hợp lệ (SCHEDULING_MAX_WEEKLY_MINUTES và SCHEDULING_MIN_WEEKLY_MINUTES).");
        }
        var week = request.weekStart();
        if (week.getDayOfWeek() != DayOfWeek.MONDAY || week.isBefore(LocalDate.of(1900, 1, 1))
            || week.isAfter(LocalDate.now(zone).plusWeeks(12))
            || new HashSet<>(request.workDates()).size() != request.workDates().size()
            || request.workDates().stream().anyMatch(day -> day.isBefore(week) || !day.isBefore(week.plusWeeks(1)))
            || new HashSet<>(request.shiftTemplateIds()).size() != request.shiftTemplateIds().size()) {
            throw new ScheduleException(HttpStatus.BAD_REQUEST, "INVALID_GENERATION_REQUEST",
                "Chọn tuần bắt đầu thứ Hai, ngày thuộc tuần đó và mẫu ca không trùng nhau.");
        }
        var actor = identity.person(actorId);
        if (actor.locked() || !Set.of("MANAGER", "ADMIN").contains(actor.role())) throw forbidden();
        UUID managerId = actor.role().equals("MANAGER") ? actorId : request.managerId();
        if (managerId == null || actor.role().equals("MANAGER") && request.managerId() != null
            && !request.managerId().equals(actorId)) throw forbidden();
        var manager = identity.lockPerson(managerId);
        if (manager.locked() || !manager.role().equals("MANAGER")) throw forbidden();

        // Manual assignment also locks the employee row. Lock all team members in a stable order before planning.
        var ids = identity.managedEmployeeIds(managerId).stream().sorted().toList();
        for (var id : ids) identity.lockPerson(id);
        var profiles = identity.scoreProfiles(ids);
        var selected = templates.findAllById(request.shiftTemplateIds());
        if (selected.size() != request.shiftTemplateIds().size()) {
            throw new ScheduleException(HttpStatus.NOT_FOUND, "SHIFT_NOT_FOUND", "Có mẫu ca không còn tồn tại.");
        }
        var byId = new HashMap<UUID, ShiftTemplate>();
        for (var template : selected) {
            if (template.getRequiredPositionId() == null || template.getRequiredHeadcount() < 1) {
                throw new ScheduleException(HttpStatus.CONFLICT, "SHIFT_POSITION_REQUIRED",
                    "Mẫu ca cũ chưa có vị trí bắt buộc hoặc số người cần hợp lệ: " + template.getName());
            }
            byId.put(template.getId(), template);
        }
        var registered = registrations.selections(week, ids);
        var rangeStart = week.minusDays(1).atStartOfDay(zone).toInstant();
        var rangeEnd = week.plusWeeks(1).plusDays(1).atStartOfDay(zone).toInstant();
        var existing = ids.isEmpty() ? List.<WorkSchedule>of()
            : schedules.findByEmployeeIdInAndStartsAtLessThanAndEndsAtGreaterThan(ids, rangeEnd, rangeStart);
        var plannedExisting = existing.stream().map(row -> new Existing(row.getEmployeeId(), row.getWorkDate(),
            row.getStartsAt(), row.getEndsAt(), workingMinutes(row))).toList();
        var scores = new HashMap<UUID, Map<UUID, Integer>>();
        var slots = new ArrayList<Slot>();
        for (var date : request.workDates().stream().sorted().toList()) {
            for (var template : selected.stream().sorted(Comparator.comparing(ShiftTemplate::getId)).toList()) {
                var positionId = template.getRequiredPositionId();
                var positionScores = scores.computeIfAbsent(positionId, ignored -> {
                    var values = new HashMap<UUID, Integer>();
                    for (var score : priority.team(actorId, managerId, week, positionId).items()) {
                        values.put(score.employeeId(), score.score());
                    }
                    return values;
                });
                var eligible = new HashMap<UUID, Integer>();
                for (var id : ids) {
                    var profile = profiles.get(id);
                    if (profile != null && !profile.locked() && managerId.equals(profile.managerId())
                        && registered.getOrDefault(id, Set.of()).contains(template.getId())
                        && qualified(profile, template) && positionScores.containsKey(id)) {
                        eligible.put(id, positionScores.get(id));
                    }
                }
                var window = ShiftWindow.of(date, template.getStartTime(), template.getEndTime(), zone);
                int filled = (int) existing.stream().filter(row -> row.getWorkDate().equals(date)
                    && row.getShiftTemplateId().equals(template.getId())
                    && qualified(profiles.get(row.getEmployeeId()), template)).count();
                int missing = Math.max(0, template.getRequiredHeadcount() - filled);
                if (slots.size() + missing > 700) {
                    throw new ScheduleException(HttpStatus.BAD_REQUEST, "SCHEDULE_TOO_LARGE",
                        "Yêu cầu xếp lịch quá lớn; hãy giảm số mẫu ca hoặc số người cần mỗi ca.");
                }
                int minutes = ShiftWindow.durationMinutes(template.getStartTime(), template.getEndTime()) - template.getBreakMinutes();
                for (int seat = 0; seat < missing; seat++) {
                    slots.add(new Slot(template.getId(), date, window.startsAt(), window.endsAt(), minutes, eligible));
                }
            }
        }
        var planner = new WeeklySchedulePlanner(week, maxWeeklyMinutes, slots, plannedExisting);
        var partial = planner.planPartial();
        var result = partial.assignments();
        var created = new ArrayList<WorkSchedule>();
        for (var assignment : result) {
            created.add(new WorkSchedule(assignment.employeeId(), byId.get(assignment.slot().templateId()),
                assignment.slot().workDate(), zone, actorId, true));
        }
        if (!created.isEmpty()) schedules.saveAllAndFlush(created);
        var items = created.stream().map(row -> ScheduleResponse.from(row,
            profiles.get(row.getEmployeeId()).name(), byId.get(row.getShiftTemplateId()).getRequiredPosition())).toList();
        return new PlanOutput(items, partial.unfilled(), byId);
    }

    private boolean qualified(ScoreProfile profile, ShiftTemplate template) {
        return profile != null && (template.getRequiredPositionId().equals(profile.positionId())
            || profile.skills().stream().anyMatch(skill -> skill.equalsIgnoreCase(template.getRequiredPosition())));
    }

    private long workingMinutes(WorkSchedule row) {
        return Math.max(0, Duration.between(row.getStartsAt(), row.getEndsAt()).toMinutes() - row.getBreakMinutes());
    }

    private ScheduleException forbidden() {
        return new ScheduleException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền xếp lịch cho nhóm này.");
    }
}
