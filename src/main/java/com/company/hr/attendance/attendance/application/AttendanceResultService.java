package com.company.hr.attendance.attendance.application;

import com.company.hr.attendance.attendance.api.AttendanceModels.*;
import com.company.hr.attendance.attendance.api.AttendanceResults.*;
import com.company.hr.attendance.attendance.domain.AttendanceClassifier;
import com.company.hr.attendance.attendance.infrastructure.AttendanceEventRepository;
import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.workschedule.WorkScheduleApi;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AttendanceResultService {
    private final WorkScheduleApi schedules;
    private final AttendanceEventRepository events;
    private final IdentityApi identity;
    private final int graceMinutes;
    public AttendanceResultService(WorkScheduleApi schedules, AttendanceEventRepository events, IdentityApi identity,
                                   @Value("${app.attendance.grace-minutes:5}") int graceMinutes) {
        this.schedules = schedules; this.events = events; this.identity = identity; this.graceMinutes = graceMinutes;
    }
    public ResultPage results(UUID actorId, LocalDate from, LocalDate to, UUID employeeId, int page, int size) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 30)
            throw new AttendanceException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "Khoảng ngày phải hợp lệ và không quá 31 ngày.");
        var shifts = schedules.pageForManager(actorId, from, to, employeeId, page, size);
        var names = identity.names(shifts.stream().map(WorkScheduleApi.Shift::employeeId).distinct().toList());
        var byShift = events.scheduleEvents(shifts.stream().map(WorkScheduleApi.Shift::id).toList()).stream()
            .collect(Collectors.groupingBy(EventResponse::scheduleId,
                Collectors.toMap(EventResponse::type, Function.identity())));
        var now = Instant.now();
        var rows = shifts.stream().map(shift -> {
            Map<EventType, EventResponse> recorded = byShift.getOrDefault(shift.id(), Map.of());
            EventResponse checkIn = recorded.get(EventType.CHECK_IN);
            EventResponse checkOut = recorded.get(EventType.CHECK_OUT);
            Instant in = checkIn == null ? null : checkIn.receivedAt();
            Instant out = checkOut == null ? null : checkOut.receivedAt();
            var status = AttendanceClassifier.classify(shift.startsAt(), shift.endsAt(), in, out, now, graceMinutes);
            return new ResultResponse(shift.id(), shift.employeeId(), names.getOrDefault(shift.employeeId(), "Nhân viên"),
                shift.workDate(), shift.name(), shift.startsAt(), shift.endsAt(), in, out,
                status.arrival(), status.departure(), status.overall(), status.lateMinutes(), status.earlyMinutes());
        }).toList();
        return new ResultPage(rows, shifts.getNumber(), shifts.getSize(), shifts.getTotalElements(), shifts.getTotalPages());
    }
}
