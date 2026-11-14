package com.company.hr.attendance.notification.application;

import com.company.hr.attendance.notification.infrastructure.PushRepository;
import com.company.hr.attendance.workschedule.domain.SchedulePublishedEvent;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShiftNotificationService {
    private static final Logger log = LoggerFactory.getLogger(ShiftNotificationService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final PushRepository pushRepository;
    private final PushGateway pushGateway;
    private final EmailGateway emailGateway;
    private final JdbcTemplate jdbc;

    public ShiftNotificationService(PushRepository pushRepository, PushGateway pushGateway, JdbcTemplate jdbc) {
        this(pushRepository, pushGateway, new NoopEmailGateway(), jdbc);
    }

    @Autowired
    public ShiftNotificationService(
        PushRepository pushRepository,
        PushGateway pushGateway,
        EmailGateway emailGateway,
        JdbcTemplate jdbc
    ) {
        this.pushRepository = pushRepository;
        this.pushGateway = pushGateway;
        this.emailGateway = emailGateway != null ? emailGateway : new NoopEmailGateway();
        this.jdbc = jdbc;
    }

    @EventListener
    public void onSchedulePublished(SchedulePublishedEvent event) {
        sendSchedulePublishedNotifications(event.employeeIds(), event.from(), event.to());
    }

    @Transactional
    public int sendSchedulePublishedNotifications(Set<UUID> employeeIds, LocalDate from, LocalDate to) {
        if (employeeIds == null || employeeIds.isEmpty()) return 0;
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofDays(7));
        String title = "Lịch làm việc đã được công bố";
        String body = String.format("Lịch làm việc từ ngày %s đến %s đã được công bố. Vui lòng kiểm tra ca làm việc của bạn.", from, to);
        Map<String, String> data = Map.of(
            "type", "SCHEDULE_PUBLISHED",
            "from", from.toString(),
            "to", to.toString(),
            "tag", "schedule-published-" + from
        );

        int sentCount = 0;
        for (UUID employeeId : employeeIds) {
            pushRepository.recordNotificationLog(employeeId, "SCHEDULE_PUBLISHED", title, body, null, now);

            EmployeeContact contact = findEmployeeContact(employeeId);
            if (contact != null && !contact.locked() && contact.email() != null && !contact.email().isBlank()) {
                String emailSubject = "[ShiftSync] " + title;
                String emailBody = String.format(
                    "Xin chào %s,\n\nLịch làm việc từ ngày %s đến %s đã được quản lý công bố trên hệ thống ShiftSync.\n" +
                    "Vui lòng đăng nhập vào ứng dụng để kiểm tra chi tiết các ca làm việc của bạn.\n\nTrân trọng,\nShiftSync",
                    contact.fullName() != null && !contact.fullName().isBlank() ? contact.fullName() : "bạn",
                    from,
                    to
                );
                if (emailGateway.sendEmail(contact.email(), emailSubject, emailBody)) {
                    sentCount++;
                }
            }

            List<String> tokens = pushRepository.findTokensByEmployeeId(employeeId);
            for (String token : tokens) {
                try {
                    var res = pushGateway.sendCustom(employeeId, token, title, body, data, expiresAt, now);
                    if (res == PushGateway.Result.SENT) sentCount++;
                } catch (Exception ex) {
                    log.warn("Failed to deliver published schedule push to employee {}", employeeId, ex);
                }
            }
        }
        return sentCount;
    }

    @Transactional
    public int sendShiftReminders(Instant now) {
        // Find shifts starting within 0 to 35 minutes from now (30 min reminder window, deduplicated per shift)
        Instant startWindow = now;
        Instant endWindow = now.plus(Duration.ofMinutes(35));

        var shifts = jdbc.query(
            "SELECT ws.id, ws.employee_id, ws.starts_at, ws.ends_at, ws.shift_name, ws.work_date, " +
            "ia.email, ia.display_name, ia.locked " +
            "FROM work_schedules ws " +
            "LEFT JOIN identity_accounts ia ON ia.id = ws.employee_id " +
            "WHERE ws.published = true AND ws.starts_at >= ? AND ws.starts_at <= ?",
            (rs, i) -> new ShiftCandidate(
                rs.getObject("id", UUID.class),
                rs.getObject("employee_id", UUID.class),
                rs.getTimestamp("starts_at").toInstant(),
                rs.getString("shift_name"),
                rs.getObject("work_date", LocalDate.class),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getBoolean("locked")
            ),
            OffsetDateTime.ofInstant(startWindow, ZoneOffset.UTC),
            OffsetDateTime.ofInstant(endWindow, ZoneOffset.UTC)
        );

        int count = 0;
        Instant expiresAt = now.plus(Duration.ofMinutes(60));
        for (var shift : shifts) {
            if (shift.locked()) {
                continue;
            }
            if (pushRepository.hasReceivedShiftReminder(shift.scheduleId(), "SHIFT_REMINDER_30M")) {
                continue;
            }

            String localTimeStr = shift.startsAt().atZone(ZONE).format(TIME_FMT);
            LocalDate workDate = shift.workDate() != null ? shift.workDate() : shift.startsAt().atZone(ZONE).toLocalDate();
            String title = "Nhắc nhở ca làm việc";
            String body = String.format("Bạn có ca làm việc '%s' sẽ bắt đầu lúc %s (sau 30 phút).", shift.shiftName(), localTimeStr);
            Map<String, String> data = Map.of(
                "type", "SHIFT_REMINDER",
                "scheduleId", shift.scheduleId().toString(),
                "tag", "shift-reminder-" + shift.scheduleId()
            );

            pushRepository.recordShiftReminder(shift.scheduleId(), shift.employeeId(), "SHIFT_REMINDER_30M", now);
            pushRepository.recordNotificationLog(shift.employeeId(), "SHIFT_REMINDER_30M", title, body, shift.scheduleId(), now);

            if (shift.email() != null && !shift.email().isBlank()) {
                String emailSubject = String.format("[ShiftSync] Nhắc nhở ca '%s' lúc %s (trước 30 phút)", shift.shiftName(), localTimeStr);
                String emailBody = String.format(
                    "Xin chào %s,\n\nHệ thống ShiftSync nhắc bạn có ca làm việc sắp bắt đầu sau khoảng 30 phút:\n" +
                    "- Ca làm việc: %s\n" +
                    "- Ngày làm việc: %s\n" +
                    "- Giờ vào ca: %s\n\n" +
                    "Vui lòng chuẩn bị và điểm danh đúng giờ.\n\nTrân trọng,\nShiftSync",
                    shift.fullName() != null && !shift.fullName().isBlank() ? shift.fullName() : "bạn",
                    shift.shiftName(),
                    workDate,
                    localTimeStr
                );
                emailGateway.sendEmail(shift.email(), emailSubject, emailBody);
            }

            List<String> tokens = pushRepository.findTokensByEmployeeId(shift.employeeId());
            for (String token : tokens) {
                try {
                    pushGateway.sendCustom(shift.employeeId(), token, title, body, data, expiresAt, now);
                } catch (Exception ex) {
                    log.warn("Failed to send 30m shift reminder push to employee {}", shift.employeeId(), ex);
                }
            }
            count++;
        }
        return count;
    }

    private EmployeeContact findEmployeeContact(UUID employeeId) {
        try {
            var rows = jdbc.query(
                "SELECT email, display_name, locked FROM identity_accounts WHERE id = ?",
                (rs, i) -> new EmployeeContact(
                    rs.getString("email"),
                    rs.getString("display_name"),
                    rs.getBoolean("locked")
                ),
                employeeId
            );
            return rows == null || rows.isEmpty() ? null : rows.getFirst();
        } catch (Exception ex) {
            log.debug("Could not query employee contact for {}", employeeId, ex);
            return null;
        }
    }

    private record EmployeeContact(String email, String fullName, boolean locked) {}

    private record ShiftCandidate(
        UUID scheduleId,
        UUID employeeId,
        Instant startsAt,
        String shiftName,
        LocalDate workDate,
        String email,
        String fullName,
        boolean locked
    ) {}

    private static final class NoopEmailGateway implements EmailGateway {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public boolean sendEmail(String toEmail, String subject, String body) {
            return false;
        }
    }
}
