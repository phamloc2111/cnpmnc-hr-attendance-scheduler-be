package com.company.hr.attendance.attendance.application;

import com.company.hr.attendance.attendance.api.AttendanceRecordModels.*;
import com.company.hr.attendance.attendance.domain.*;
import com.company.hr.attendance.attendance.infrastructure.AttendanceRecordRepository;
import com.company.hr.attendance.identity.IdentityApi;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.http.HttpStatus.*;

@Service
@Transactional(readOnly = true)
public class AttendanceRecordService {
    private final IdentityApi identity;
    private final AttendanceRecordRepository records;

    public AttendanceRecordService(IdentityApi identity, AttendanceRecordRepository records) {
        this.identity = identity;
        this.records = records;
    }

    public PageResponse<AttendanceRecordResponse> list(
        UUID actorId,
        LocalDate from,
        LocalDate to,
        UUID employeeId,
        AttendanceStatus status,
        AttendanceType checkType,
        int page,
        int size
    ) {
        if (from != null && to != null && (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 62)) {
            throw new AttendanceException(BAD_REQUEST, "INVALID_DATE_RANGE", "Khoảng ngày không hợp lệ hoặc vượt quá 62 ngày.");
        }
        var actor = active(actorId);
        Specification<AttendanceRecord> filter = (root, ignored, cb) -> cb.conjunction();
        if (from != null) {
            filter = filter.and((root, ignored, cb) -> cb.greaterThanOrEqualTo(root.get("workDate"), from));
        }
        if (to != null) {
            filter = filter.and((root, ignored, cb) -> cb.lessThanOrEqualTo(root.get("workDate"), to));
        }
        if (actor.role().equals("EMPLOYEE")) {
            if (employeeId != null && !employeeId.equals(actorId)) throw forbidden();
            filter = filter.and((root, ignored, cb) -> cb.equal(root.get("employeeId"), actorId));
        } else if (actor.role().equals("MANAGER")) {
            var managedIds = identity.managedEmployeeIds(actorId);
            if (employeeId != null && !managedIds.contains(employeeId)) throw notFound();
            filter = filter.and((root, ignored, cb) ->
                managedIds.isEmpty() ? cb.disjunction() : root.get("employeeId").in(managedIds));
        }
        if (employeeId != null) {
            filter = filter.and((root, ignored, cb) -> cb.equal(root.get("employeeId"), employeeId));
        }
        if (status != null) {
            filter = filter.and((root, ignored, cb) -> cb.equal(root.get("status"), status));
        }
        if (checkType != null) {
            filter = filter.and((root, ignored, cb) -> cb.equal(root.get("checkType"), checkType));
        }
        var sort = Sort.by(Sort.Order.desc("recordedAt"), Sort.Order.asc("id"));
        var result = records.findAll(filter, PageRequest.of(page, size, sort));
        var names = identity.names(result.stream().map(AttendanceRecord::getEmployeeId).distinct().toList());
        return PageResponse.from(result.map(record -> AttendanceRecordResponse.from(
            record,
            names.getOrDefault(record.getEmployeeId(), "Nhân viên")
        )));
    }

    public AttendanceRecordResponse get(UUID actorId, UUID id) {
        var actor = active(actorId);
        var record = records.findById(id).orElseThrow(AttendanceRecordService::notFound);
        if (actor.role().equals("EMPLOYEE") && !record.getEmployeeId().equals(actorId)) {
            throw notFound();
        }
        if (actor.role().equals("MANAGER") && !identity.managedEmployeeIds(actorId).contains(record.getEmployeeId())) {
            throw notFound();
        }
        var names = identity.names(List.of(record.getEmployeeId()));
        return AttendanceRecordResponse.from(record, names.getOrDefault(record.getEmployeeId(), "Nhân viên"));
    }

    @Transactional
    public AttendanceRecordResponse create(UUID actorId, CreateAttendanceRequest request) {
        var actor = active(actorId);
        var targetEmployeeId = request.employeeId() != null ? request.employeeId() : actorId;
        if (actor.role().equals("EMPLOYEE") && !targetEmployeeId.equals(actorId)) {
            throw forbidden();
        }
        var employee = identity.person(targetEmployeeId);
        if (!employee.role().equals("EMPLOYEE") || employee.locked()) {
            throw new AttendanceException(BAD_REQUEST, "INVALID_EMPLOYEE", "Nhân viên điểm danh không hợp lệ hoặc đã bị khóa.");
        }
        if (actor.role().equals("MANAGER") && !actorId.equals(employee.managerId())) {
            throw notFound();
        }
        var recordedAt = request.recordedAt() != null ? request.recordedAt() : Instant.now();
        var record = new AttendanceRecord(
            employee.id(),
            request.scheduleId(),
            request.workDate(),
            request.shiftName(),
            request.checkType(),
            recordedAt,
            request.photoUrl(),
            request.photoCapturedAt() != null ? request.photoCapturedAt() : recordedAt,
            request.photoLocation(),
            request.deviceLocation(),
            request.deviceLatitude(),
            request.deviceLongitude(),
            request.status(),
            request.verificationNote()
        );
        return AttendanceRecordResponse.from(records.saveAndFlush(record), employee.name());
    }

    private IdentityApi.Person active(UUID id) {
        var person = identity.person(id);
        if (person.locked()) throw forbidden();
        return person;
    }

    private static AttendanceException forbidden() {
        return new AttendanceException(FORBIDDEN, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này.");
    }

    private static AttendanceException notFound() {
        return new AttendanceException(NOT_FOUND, "ATTENDANCE_NOT_FOUND", "Không tìm thấy lượt điểm danh trong phạm vi quản lý.");
    }
}
