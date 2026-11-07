package com.company.hr.attendance.workschedule.application;

import com.company.hr.attendance.attendance.AttendanceApi;
import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.organization.PositionsApi;
import com.company.hr.attendance.workschedule.api.ScheduleModels.*;
import com.company.hr.attendance.workschedule.domain.*;
import com.company.hr.attendance.workschedule.infrastructure.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.http.HttpStatus.*;

@Service
@Transactional(readOnly = true)
public class ScheduleService {
    private final IdentityApi identity;
    private final ShiftTemplateRepository shifts;
    private final WorkScheduleRepository schedules;
    private final ZoneId zone;
    private final PositionsApi positions;
    private final AttendanceApi attendance;
    public ScheduleService(IdentityApi identity, ShiftTemplateRepository shifts, WorkScheduleRepository schedules,
                           @Value("${app.timezone:Asia/Ho_Chi_Minh}") String timezone, PositionsApi positions,
                           AttendanceApi attendance) {
        this.identity = identity; this.shifts = shifts; this.schedules = schedules; this.zone = ZoneId.of(timezone);
        this.positions = positions; this.attendance = attendance;
    }
    public PageResponse<ShiftResponse> shifts(UUID actorId, int page, int size) {
        management(actorId);
        return PageResponse.from(shifts.findAll(PageRequest.of(page, size, Sort.by("name", "id"))).map(ShiftResponse::from));
    }
    @Transactional
    public ShiftResponse createShift(UUID actorId, CreateShift request) {
        management(actorId);
        var start = request.startTime(); var end = request.endTime();
        if (start.equals(end) || start.getSecond() != 0 || end.getSecond() != 0
            || start.getNano() != 0 || end.getNano() != 0
            || request.breakMinutes() >= ShiftWindow.durationMinutes(start, end)) {
            throw new ScheduleException(BAD_REQUEST, "INVALID_SHIFT",
                "Giờ ca phải khác nhau, chính xác đến phút; thời gian nghỉ phải ngắn hơn ca làm.");
        }
        if (request.overnight() != end.isBefore(start)) {
            throw new ScheduleException(BAD_REQUEST, "INVALID_OVERNIGHT",
                "Chỉ đánh dấu ca qua đêm khi giờ kết thúc nhỏ hơn giờ bắt đầu.");
        }
        var selected = positions.select(request.requiredPositionId(), null);
        return ShiftResponse.from(shifts.save(new ShiftTemplate(request.name().strip(), start, end,
            request.breakMinutes(), actorId, request.requiredHeadcount(), selected.name(), selected.id(), request.overnight())));
    }
    public PageResponse<ScheduleResponse> list(UUID actorId, LocalDate from, LocalDate to, UUID employeeId, int page, int size) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 30) {
            throw new ScheduleException(BAD_REQUEST, "INVALID_DATE_RANGE", "Khoảng ngày phải hợp lệ và không quá 31 ngày.");
        }
        var actor = active(actorId);
        Specification<WorkSchedule> filter = (root, ignored, cb) ->
            cb.between(root.get("workDate"), from, to);
        if (actor.role().equals("EMPLOYEE")) {
            if (employeeId != null && !employeeId.equals(actorId)) throw forbidden();
            filter = filter.and((root, ignored, cb) -> cb.equal(root.get("employeeId"), actorId));
        } else if (actor.role().equals("MANAGER")) {
            var ids = identity.managedEmployeeIds(actorId);
            if (employeeId != null && !ids.contains(employeeId)) throw notFound();
            filter = filter.and((root, ignored, cb) -> ids.isEmpty() ? cb.disjunction() : root.get("employeeId").in(ids));
        }
        if (employeeId != null) filter = filter.and((root, ignored, cb) -> cb.equal(root.get("employeeId"), employeeId));
        var result = schedules.findAll(filter, PageRequest.of(page, size, Sort.by("workDate", "startsAt", "id")));
        var names = identity.names(result.stream().map(WorkSchedule::getEmployeeId).distinct().toList());
        return PageResponse.from(result.map(schedule -> ScheduleResponse.from(schedule,
            names.getOrDefault(schedule.getEmployeeId(), "Nhân viên"))));
    }
    @Transactional
    public ScheduleResponse assign(UUID actorId, AssignShift request) {
        var actor = management(actorId);
        // All schedule writes for one employee share this row lock, including concurrent HTTP requests.
        var employee = identity.lockPerson(request.employeeId());
        checkEmployee(actor, employee);
        if (employee.locked()) throw new ScheduleException(CONFLICT, "EMPLOYEE_LOCKED", "Nhân viên đang bị khóa tài khoản.");
        var shift = shifts.findById(request.shiftTemplateId()).orElseThrow(() ->
            new ScheduleException(NOT_FOUND, "SHIFT_NOT_FOUND", "Không tìm thấy mẫu ca."));
        var schedule = new WorkSchedule(employee.id(), shift, request.workDate(), zone, actorId);
        if (schedules.existsByEmployeeIdAndStartsAtLessThanAndEndsAtGreaterThan(
            employee.id(), schedule.getEndsAt(), schedule.getStartsAt())) {
            throw new ScheduleException(CONFLICT, "SCHEDULE_OVERLAP", "Nhân viên đã có ca trùng giờ, kể cả ca từ ngày trước.");
        }
        return ScheduleResponse.from(schedules.saveAndFlush(schedule), employee.name());
    }
    @Transactional
    public void cancel(UUID actorId, UUID id) {
        var actor = management(actorId);
        var schedule = schedules.findById(id).orElseThrow(ScheduleService::notFound);
        var employee = identity.lockPerson(schedule.getEmployeeId());
        // Admin can also remove old schedules after an employee's role has changed.
        if (!actor.role().equals("ADMIN")) checkEmployee(actor, employee);
        if (attendance.hasEvents(id)) throw new ScheduleException(CONFLICT, "SCHEDULE_HAS_ATTENDANCE",
            "Ca đã có lượt điểm danh; không thể hủy.");
        schedules.delete(schedule);
        schedules.flush();
    }
    private IdentityApi.Person active(UUID id) {
        var person = identity.person(id);
        if (person.locked()) throw forbidden();
        return person;
    }
    private IdentityApi.Person management(UUID id) {
        var actor = active(id);
        if (!Set.of("ADMIN", "MANAGER").contains(actor.role())) throw forbidden();
        return actor;
    }
    private void checkEmployee(IdentityApi.Person actor, IdentityApi.Person employee) {
        if (!employee.role().equals("EMPLOYEE") ||
            actor.role().equals("MANAGER") && !actor.id().equals(employee.managerId())) throw notFound();
    }
    private static ScheduleException forbidden() {
        return new ScheduleException(FORBIDDEN, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này.");
    }
    private static ScheduleException notFound() {
        return new ScheduleException(NOT_FOUND, "SCHEDULE_NOT_FOUND", "Không tìm thấy lịch hoặc nhân viên trong phạm vi quản lý.");
    }
}
