package com.company.hr.attendance.notification.application;

import com.company.hr.attendance.notification.infrastructure.PushRepository;
import com.company.hr.attendance.workschedule.domain.SchedulePublishedEvent;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final JdbcTemplate jdbc;

    public ShiftNotificationService(PushRepository pushRepository, PushGateway pushGateway, JdbcTemplate jdbc) {
        this.pushRepository = pushRepository;
        this.pushGateway = pushGateway;
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
        // Find shifts starting within 25 to 35 minutes from now (30 min reminder)
        Instant startWindow = now.plus(Duration.ofMinutes(25));
        Instant endWindow = now.plus(Duration.ofMinutes(35));

        var shifts = jdbc.query(
            "SELECT ws.id, ws.employee_id, ws.starts_at, ws.ends_at, ws.shift_name " +
            "FROM work_schedules ws " +
            "WHERE ws.published = true AND ws.starts_at >= ? AND ws.starts_at <= ?",
            (rs, i) -> new ShiftCandidate(
                rs.getObject("id", UUID.class),
                rs.getObject("employee_id", UUID.class),
                rs.getTimestamp("starts_at").toInstant(),
                rs.getString("shift_name")
            ),
            OffsetDateTime.ofInstant(startWindow, ZoneOffset.UTC),
            OffsetDateTime.ofInstant(endWindow, ZoneOffset.UTC)
        );

        int count = 0;
        Instant expiresAt = now.plus(Duration.ofMinutes(60));
        for (var shift : shifts) {
            if (pushRepository.hasReceivedShiftReminder(shift.scheduleId(), "SHIFT_REMINDER_30M")) {
                continue;
            }

            String localTimeStr = shift.startsAt().atZone(ZONE).format(TIME_FMT);
            String title = "Nhắc nhở ca làm việc";
            String body = String.format("Bạn có ca làm việc '%s' sẽ bắt đầu lúc %s (sau 30 phút).", shift.shiftName(), localTimeStr);
            Map<String, String> data = Map.of(
                "type", "SHIFT_REMINDER",
                "scheduleId", shift.scheduleId().toString(),
                "tag", "shift-reminder-" + shift.scheduleId()
            );

            pushRepository.recordShiftReminder(shift.scheduleId(), shift.employeeId(), "SHIFT_REMINDER_30M", now);
            pushRepository.recordNotificationLog(shift.employeeId(), "SHIFT_REMINDER_30M", title, body, shift.scheduleId(), now);

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

    private record ShiftCandidate(UUID scheduleId, UUID employeeId, Instant startsAt, String shiftName) {}
}
