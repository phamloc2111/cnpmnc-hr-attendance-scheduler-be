package com.company.hr.attendance.attendance.application;

import com.company.hr.attendance.attendance.api.AttendanceModels.*;
import com.company.hr.attendance.attendance.infrastructure.AttendanceEventRepository;
import com.company.hr.attendance.attendance.infrastructure.AttendanceRecordRepository;
import com.company.hr.attendance.attendance.domain.AttendanceRecord;
import com.company.hr.attendance.attendance.domain.AttendanceStatus;
import com.company.hr.attendance.attendance.domain.AttendanceType;
import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.workschedule.WorkScheduleApi;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class AttendanceService {
    private static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;
    private final AttendanceEventRepository events;
    private final AttendanceRecordRepository records;
    private final WorkScheduleApi schedules;
    private final IdentityApi identity;
    private final TimemarkReader timemarkReader;
    private final AttendanceVerificationService verification;
    private final int graceMinutes;
    public AttendanceService(AttendanceEventRepository events, AttendanceRecordRepository records,
                             WorkScheduleApi schedules, IdentityApi identity, TimemarkReader timemarkReader,
                             AttendanceVerificationService verification,
                             @Value("${app.attendance.grace-minutes:5}") int graceMinutes) {
        this.events = events; this.records = records; this.schedules = schedules; this.identity = identity;
        this.timemarkReader = timemarkReader; this.verification = verification;
        this.graceMinutes = graceMinutes;
    }

    public TimemarkAnalysisResponse analyzeTimemark(MultipartFile image) { return timemarkReader.read(image); }

    @Transactional
    public EventResponse record(UUID actorId, UUID scheduleId, EventType type, MultipartFile photo) {
        var actor = identity.person(actorId);
        if (actor.locked() || !actor.role().equals("EMPLOYEE")) throw forbidden();
        if (scheduleId == null || type == null)
            throw invalid("Cần chọn ca và lượt vào/ra khi gửi ảnh.");
        if (photo == null || photo.isEmpty() || photo.getSize() > MAX_PHOTO_BYTES)
            throw invalid("Ảnh Timemark phải có dung lượng từ 1 byte đến 5 MB.");
        byte[] bytes;
        try { bytes = photo.getBytes(); }
        catch (IOException error) { throw invalid("Không đọc được ảnh. Vui lòng chọn lại."); }
        if (bytes.length == 0 || bytes.length > MAX_PHOTO_BYTES) throw invalid("Ảnh Timemark vượt quá 5 MB.");
        String contentType;
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) contentType = "image/jpeg";
        else if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47 && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) contentType = "image/png";
        else if (bytes.length >= 12 && bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46 && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) contentType = "image/webp";
        else throw invalid("Chỉ hỗ trợ ảnh JPEG, PNG hoặc WEBP.");

        var analysis = timemarkReader.read(photo);
        if (analysis == null || !analysis.readable()) {
            var reason = analysis != null && org.springframework.util.StringUtils.hasText(analysis.reason())
                ? analysis.reason() : "Ảnh không đọc được hoặc không phải ảnh Timemark hợp lệ.";
            throw new AttendanceException(HttpStatus.UNPROCESSABLE_ENTITY, "TIMEMARK_UNREADABLE", reason);
        }
        if (!org.springframework.util.StringUtils.hasText(analysis.printedDateTime())) {
            throw new AttendanceException(HttpStatus.UNPROCESSABLE_ENTITY, "TIMEMARK_MISSING_TIME",
                "Ảnh thiếu thời gian cần thiết để xác minh.");
        }
        if (analysis.latitude() == null || analysis.longitude() == null) {
            throw new AttendanceException(HttpStatus.UNPROCESSABLE_ENTITY, "TIMEMARK_MISSING_COORDINATES",
                "Ảnh thiếu kinh độ/vĩ độ cần thiết để xác minh.");
        }

        Instant capturedAt;
        try {
            capturedAt = java.time.OffsetDateTime.parse(analysis.printedDateTime()).toInstant();
        } catch (Exception e1) {
            try {
                capturedAt = Instant.parse(analysis.printedDateTime());
            } catch (Exception e2) {
                throw new AttendanceException(HttpStatus.UNPROCESSABLE_ENTITY, "TIMEMARK_INVALID_TIME",
                    "Thời gian trên ảnh Timemark không đúng định dạng.");
            }
        }

        double lat = analysis.latitude();
        double lon = analysis.longitude();
        if (!Double.isFinite(lat) || lat < -90 || lat > 90 || !Double.isFinite(lon) || lon < -180 || lon > 180) {
            throw new AttendanceException(HttpStatus.UNPROCESSABLE_ENTITY, "TIMEMARK_INVALID_COORDINATES",
                "Tọa độ trên ảnh Timemark không hợp lệ.");
        }

        var receivedAt = Instant.now();
        var timemark = new TimemarkEvidence(capturedAt, lat, lon);
        verification.verify(timemark, receivedAt);

        // The schedule row lock serializes check-in/check-out and prevents duplicate concurrent uploads.
        var schedule = schedules.lock(scheduleId).orElseThrow(() ->
            new AttendanceException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Không tìm thấy ca làm việc."));
        if (!schedule.employeeId().equals(actorId)) throw forbidden();
        if (type == EventType.CHECK_IN && !receivedAt.isBefore(schedule.endsAt()))
            throw new AttendanceException(HttpStatus.CONFLICT, "SHIFT_ENDED", "Ca đã kết thúc; không thể chấm vào.");
        if (type == EventType.CHECK_OUT && !events.exists(scheduleId, EventType.CHECK_IN))
            throw new AttendanceException(HttpStatus.CONFLICT, "CHECK_IN_REQUIRED", "Cần chấm vào trước khi chấm ra.");
        if (events.exists(scheduleId, type))
            throw new AttendanceException(HttpStatus.CONFLICT, "ALREADY_RECORDED", "Lượt điểm danh này đã được ghi nhận.");
        var id = UUID.randomUUID();
        try { events.insert(id, scheduleId, actorId, type, receivedAt, capturedAt,
            lat, lon, 0.0, contentType, bytes); }
        catch (DataIntegrityViolationException error) {
            throw new AttendanceException(HttpStatus.CONFLICT, "ALREADY_RECORDED", "Lượt điểm danh đã được ghi nhận hoặc ca vừa thay đổi.");
        }
        var status = type == EventType.CHECK_IN
            ? (receivedAt.isAfter(schedule.startsAt().plus(Duration.ofMinutes(graceMinutes)))
                ? AttendanceStatus.LATE : AttendanceStatus.ON_TIME)
            : (receivedAt.isBefore(schedule.endsAt().minus(Duration.ofMinutes(graceMinutes)))
                ? AttendanceStatus.EARLY_LEAVE : AttendanceStatus.ON_TIME);
        records.saveAndFlush(new AttendanceRecord(id, actorId, scheduleId, schedule.workDate(), schedule.name(),
            AttendanceType.valueOf(type.name()), receivedAt,
            "/api/v1/attendance/events/" + id + "/photo", capturedAt,
            lat + ", " + lon,
            lat + ", " + lon, lat, lon, status,
            "Đã đối chiếu ảnh Timemark với vị trí GPS."));
        return new EventResponse(id, scheduleId, type, receivedAt, capturedAt, lat, lon, 0.0,
            "/api/v1/attendance/events/" + id + "/photo");
    }

    @Deprecated
    @Transactional
    public EventResponse record(UUID actorId, UUID scheduleId, EventType type, Instant locatedAt,
                                Double latitude, Double longitude, Double accuracy,
                                TimemarkEvidence timemark, MultipartFile photo) {
        return record(actorId, scheduleId, type, photo);
    }
    public List<EventResponse> mine(UUID actorId, LocalDate from, LocalDate to) {
        var actor = identity.person(actorId);
        if (actor.locked() || !actor.role().equals("EMPLOYEE")) throw forbidden();
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 30)
            throw invalid("Khoảng ngày phải hợp lệ và không quá 31 ngày.");
        return events.employeeEvents(actorId, from, to);
    }
    public Photo photo(UUID actorId, UUID eventId) {
        var actor = identity.person(actorId);
        if (actor.locked()) throw forbidden();
        var employeeId = events.employeeId(eventId);
        if (employeeId == null) throw notFound();
        if (!actorId.equals(employeeId) && !actor.role().equals("ADMIN") &&
            !(actor.role().equals("MANAGER") && identity.managedEmployeeIds(actorId).contains(employeeId))) throw forbidden();
        return events.photo(eventId).orElseThrow(AttendanceService::notFound);
    }
    private static AttendanceException invalid(String message) {
        return new AttendanceException(HttpStatus.BAD_REQUEST, "INVALID_ATTENDANCE", message);
    }
    private static AttendanceException forbidden() {
        return new AttendanceException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền điểm danh hoặc xem ảnh này.");
    }
    private static AttendanceException notFound() {
        return new AttendanceException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy lượt điểm danh.");
    }
}
