package com.company.hr.attendance.attendance.application;

import com.company.hr.attendance.attendance.api.AttendanceModels.*;
import com.company.hr.attendance.attendance.infrastructure.AttendanceEventRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class AttendanceService {
    private static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;
    private final AttendanceEventRepository events;
    private final WorkScheduleApi schedules;
    private final IdentityApi identity;
    private final TimemarkReader timemarkReader;
    public AttendanceService(AttendanceEventRepository events, WorkScheduleApi schedules, IdentityApi identity,
                             TimemarkReader timemarkReader) {
        this.events = events; this.schedules = schedules; this.identity = identity; this.timemarkReader = timemarkReader;
    }

    public TimemarkAnalysisResponse analyzeTimemark(MultipartFile image) { return timemarkReader.read(image); }

    @Transactional
    public EventResponse record(UUID actorId, UUID scheduleId, EventType type, Instant locatedAt,
                                Double latitude, Double longitude, Double accuracy, MultipartFile photo) {
        var actor = identity.person(actorId);
        if (actor.locked() || !actor.role().equals("EMPLOYEE")) throw forbidden();
        if (scheduleId == null || type == null || locatedAt == null || latitude == null || longitude == null || accuracy == null)
            throw invalid("Cần chọn ca, lượt vào/ra và cấp vị trí hiện tại khi gửi ảnh.");
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90 ||
            !Double.isFinite(longitude) || longitude < -180 || longitude > 180 ||
            !Double.isFinite(accuracy) || accuracy < 0 || accuracy > 10000)
            throw invalid("Tọa độ hoặc độ chính xác vị trí không hợp lệ.");
        var receivedAt = Instant.now();
        if (locatedAt.isAfter(receivedAt.plusSeconds(30)) ||
            Duration.between(locatedAt, receivedAt).compareTo(Duration.ofMinutes(2)) > 0)
            throw invalid("Vị trí đã cũ. Hãy bật định vị và gửi lại ảnh.");
        if (photo == null || photo.isEmpty() || photo.getSize() > MAX_PHOTO_BYTES)
            throw invalid("Ảnh Timemark phải có dung lượng từ 1 byte đến 5 MB.");
        byte[] bytes;
        try { bytes = photo.getBytes(); }
        catch (IOException error) { throw invalid("Không đọc được ảnh. Vui lòng chọn lại."); }
        if (bytes.length == 0 || bytes.length > MAX_PHOTO_BYTES) throw invalid("Ảnh Timemark vượt quá 5 MB.");
        String contentType;
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) contentType = "image/jpeg";
        else if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47 && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) contentType = "image/png";
        else throw invalid("Chỉ hỗ trợ ảnh JPEG hoặc PNG.");

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
        try { events.insert(id, scheduleId, actorId, type, receivedAt, locatedAt,
            latitude, longitude, accuracy, contentType, bytes); }
        catch (DataIntegrityViolationException error) {
            throw new AttendanceException(HttpStatus.CONFLICT, "ALREADY_RECORDED", "Lượt điểm danh đã được ghi nhận hoặc ca vừa thay đổi.");
        }
        return new EventResponse(id, scheduleId, type, receivedAt, locatedAt, latitude, longitude, accuracy,
            "/api/v1/attendance/events/" + id + "/photo");
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
