package com.company.hr.attendance.attendance.application;

import com.company.hr.attendance.attendance.api.AttendanceRecordModels.*;
import com.company.hr.attendance.attendance.domain.*;
import com.company.hr.attendance.attendance.infrastructure.AttendanceRecordRepository;
import com.company.hr.attendance.attendance.infrastructure.RegisteredDeviceRepository;
import com.company.hr.attendance.identity.IdentityApi;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
public class AttendanceRecordService {
    private final IdentityApi identity;
    private final AttendanceRecordRepository records;
    private final RegisteredDeviceRepository devices;
    private final long maxPhotoAgeMinutes;

    public AttendanceRecordService(
        IdentityApi identity,
        AttendanceRecordRepository records,
        RegisteredDeviceRepository devices,
        @Value("${app.attendance.max-photo-age-minutes:5}") long maxPhotoAgeMinutes
    ) {
        this.identity = identity;
        this.records = records;
        this.devices = devices;
        this.maxPhotoAgeMinutes = maxPhotoAgeMinutes;
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

    @Transactional(noRollbackFor = AttendanceException.class)
    public AttendanceRecordResponse create(UUID actorId, CreateAttendanceRequest request) {
        var actor = active(actorId);
        var targetEmployeeId = request.employeeId() != null ? request.employeeId() : actorId;
        if (actor.role().equals("EMPLOYEE") && !targetEmployeeId.equals(actorId)) {
            throw forbidden();
        }
        if (actor.role().equals("EMPLOYEE")
            && request.photoCapturedAt() == null
            && request.photoHash() == null
            && request.deviceId() == null
            && request.mockLocation() == null
            && request.continuousTracking() == null) {
            throw forbidden();
        }
        var employee = identity.person(targetEmployeeId);
        if (!employee.role().equals("EMPLOYEE") || employee.locked()) {
            throw new AttendanceException(BAD_REQUEST, "INVALID_EMPLOYEE", "Nhân viên điểm danh không hợp lệ hoặc đã bị khóa.");
        }
        if (actor.role().equals("MANAGER") && !actorId.equals(employee.managerId())) {
            throw notFound();
        }
        if (Boolean.TRUE.equals(request.continuousTracking())) {
            throw new AttendanceException(
                BAD_REQUEST,
                "CONTINUOUS_TRACKING_FORBIDDEN",
                "Chỉ lấy vị trí tại thời điểm chấm công, không theo dõi liên tục."
            );
        }

        var recordedAt = request.recordedAt() != null ? request.recordedAt() : Instant.now();
        var photoCapturedAt = request.photoCapturedAt() != null ? request.photoCapturedAt() : recordedAt;
        var photoHash = resolvePhotoHash(request.photoHash(), request.photoUrl());
        var deviceId = request.deviceId() == null || request.deviceId().isBlank() ? null : request.deviceId().strip();

        // 1. Device binding check (1 account -> 1 registered device)
        if (deviceId != null) {
            var registered = devices.findById(employee.id()).orElse(null);
            if (registered == null) {
                devices.saveAndFlush(new RegisteredDevice(
                    employee.id(),
                    deviceId,
                    request.deviceName(),
                    recordedAt,
                    actorId
                ));
            } else if (!registered.getDeviceId().equals(deviceId)) {
                saveFraudRecord(
                    employee.id(),
                    request,
                    recordedAt,
                    photoHash,
                    photoCapturedAt,
                    FraudType.UNREGISTERED_DEVICE,
                    "Từ chối: Thiết bị (" + deviceId + ") khác với thiết bị đã đăng ký (" + registered.getDeviceId() + ")"
                );
                throw new AttendanceException(
                    FORBIDDEN,
                    "UNREGISTERED_DEVICE",
                    "Thiết bị chưa được đăng ký cho tài khoản này. Vui lòng nhờ quản lý đăng ký lại khi đổi máy."
                );
            }
        }

        // 2. Fake / Mock location check -> Reject & alert manager
        if (Boolean.TRUE.equals(request.mockLocation())) {
            saveFraudRecord(
                employee.id(),
                request,
                recordedAt,
                photoHash,
                photoCapturedAt,
                FraudType.FAKE_LOCATION,
                "Cảnh báo gian lận: Phát hiện dấu hiệu định vị giả (Mock GPS) — đã báo cho quản lý"
            );
            throw new AttendanceException(
                BAD_REQUEST,
                "FAKE_LOCATION",
                "Từ chối điểm danh vì phát hiện dấu hiệu định vị giả. Đã báo cho quản lý."
            );
        }

        // 3. Stale photo check -> Time on photo deviates from server time beyond threshold
        long skewSeconds = Math.abs(Duration.between(photoCapturedAt, recordedAt).toSeconds());
        if (skewSeconds > maxPhotoAgeMinutes * 60L) {
            saveFraudRecord(
                employee.id(),
                request,
                recordedAt,
                photoHash,
                photoCapturedAt,
                FraudType.STALE_PHOTO,
                "Từ chối vì ảnh cũ: Thời gian trên ảnh lệch " + (skewSeconds / 60) + " phút so với giờ máy chủ"
            );
            throw new AttendanceException(
                BAD_REQUEST,
                "STALE_PHOTO",
                "Từ chối điểm danh vì ảnh cũ (thời gian trên ảnh lệch giờ máy chủ quá ngưỡng cho phép)."
            );
        }

        // 4. Reused photo check -> Photo already used in a previous turn
        if (records.existsByPhotoHashAndStatusNot(photoHash, AttendanceStatus.REJECTED)) {
            saveFraudRecord(
                employee.id(),
                request,
                recordedAt,
                photoHash,
                photoCapturedAt,
                FraudType.DUPLICATE_PHOTO,
                "Từ chối vì ảnh dùng lại: Mã băm ảnh (" + photoHash.substring(0, Math.min(12, photoHash.length())) + "...) trùng với lượt trước"
            );
            throw new AttendanceException(
                CONFLICT,
                "DUPLICATE_PHOTO",
                "Từ chối điểm danh vì ảnh đã được dùng ở một lượt trước."
            );
        }

        var record = new AttendanceRecord(
            employee.id(),
            request.scheduleId(),
            request.workDate(),
            request.shiftName(),
            request.checkType(),
            recordedAt,
            request.photoUrl(),
            photoHash,
            photoCapturedAt,
            request.photoLocation(),
            request.deviceLocation(),
            request.deviceLatitude(),
            request.deviceLongitude(),
            deviceId,
            request.deviceName(),
            false,
            null,
            request.status(),
            request.verificationNote()
        );
        return AttendanceRecordResponse.from(records.saveAndFlush(record), employee.name());
    }

    public List<RegisteredDeviceResponse> listDevices(UUID actorId) {
        var actor = active(actorId);
        List<RegisteredDevice> list;
        if (actor.role().equals("EMPLOYEE")) {
            list = devices.findById(actorId).map(List::of).orElse(List.of());
        } else if (actor.role().equals("MANAGER")) {
            var managedIds = identity.managedEmployeeIds(actorId);
            list = managedIds.isEmpty() ? List.of() : devices.findByEmployeeIdIn(managedIds);
        } else {
            list = devices.findAll();
        }
        var names = identity.names(list.stream().map(RegisteredDevice::getEmployeeId).distinct().toList());
        return list.stream()
            .sorted(Comparator.comparing(RegisteredDevice::getRegisteredAt).reversed())
            .map(device -> RegisteredDeviceResponse.from(
                device,
                names.getOrDefault(device.getEmployeeId(), "Nhân viên")
            ))
            .toList();
    }

    @Transactional
    public RegisteredDeviceResponse registerDevice(UUID actorId, UUID employeeId, RegisterDeviceRequest request) {
        var actor = active(actorId);
        if (actor.role().equals("EMPLOYEE")) {
            throw forbidden();
        }
        var employee = identity.person(employeeId);
        if (!employee.role().equals("EMPLOYEE") || employee.locked()) {
            throw new AttendanceException(BAD_REQUEST, "INVALID_EMPLOYEE", "Nhân viên không hợp lệ hoặc đã bị khóa.");
        }
        if (actor.role().equals("MANAGER") && !actorId.equals(employee.managerId())) {
            throw notFound();
        }
        var existing = devices.findById(employeeId).orElse(null);
        RegisteredDevice saved;
        if (existing == null) {
            saved = devices.saveAndFlush(new RegisteredDevice(
                employeeId,
                request.deviceId(),
                request.deviceName(),
                Instant.now(),
                actorId
            ));
        } else {
            existing.rebind(request.deviceId(), request.deviceName(), actorId);
            saved = devices.saveAndFlush(existing);
        }
        return RegisteredDeviceResponse.from(saved, employee.name());
    }

    @Transactional
    public void resetDevice(UUID actorId, UUID employeeId) {
        var actor = active(actorId);
        if (actor.role().equals("EMPLOYEE")) {
            throw forbidden();
        }
        var employee = identity.person(employeeId);
        if (actor.role().equals("MANAGER") && !actorId.equals(employee.managerId())) {
            throw notFound();
        }
        devices.deleteById(employeeId);
        devices.flush();
    }

    private void saveFraudRecord(
        UUID employeeId,
        CreateAttendanceRequest request,
        Instant recordedAt,
        String photoHash,
        Instant photoCapturedAt,
        FraudType fraudType,
        String note
    ) {
        var rejected = new AttendanceRecord(
            employeeId,
            request.scheduleId(),
            request.workDate(),
            request.shiftName(),
            request.checkType(),
            recordedAt,
            request.photoUrl(),
            photoHash,
            photoCapturedAt,
            request.photoLocation(),
            request.deviceLocation(),
            request.deviceLatitude(),
            request.deviceLongitude(),
            request.deviceId(),
            request.deviceName(),
            Boolean.TRUE.equals(request.mockLocation()),
            fraudType,
            AttendanceStatus.REJECTED,
            note
        );
        records.saveAndFlush(rejected);
    }

    private static String resolvePhotoHash(String providedHash, String photoUrl) {
        if (providedHash != null && !providedHash.isBlank()) {
            return providedHash.strip();
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                .digest(photoUrl.strip().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
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
