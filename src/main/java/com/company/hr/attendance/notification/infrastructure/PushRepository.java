package com.company.hr.attendance.notification.infrastructure;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PushRepository {
    public record Job(UUID id, UUID deviceId, UUID employeeId, UUID sessionId, String token, LocalDate weekStart, String kind, int attempts, UUID leaseToken) {}
    public record NotificationLogEntry(UUID id, UUID employeeId, String kind, String title, String body, UUID referenceId, Instant sentAt) {}
    public record DeviceTarget(UUID id, UUID employeeId, UUID sessionId, String token) {}
    private final JdbcTemplate jdbc;
    public PushRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void subscribe(UUID employee, UUID session, String token, String hash, Instant now) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", (org.springframework.jdbc.core.RowCallbackHandler) row -> {}, hash);
        // Token may rotate or move between accounts on a shared browser. A new device ID clears old queued ownership.
        jdbc.update("DELETE FROM notification_devices WHERE token_hash=? AND (employee_id<>? OR session_id<>?)", hash, employee, session);
        jdbc.update("INSERT INTO notification_devices(id,employee_id,session_id,token,token_hash,updated_at) VALUES (?,?,?,?,?,?) " +
            "ON CONFLICT(token_hash) DO UPDATE SET token=EXCLUDED.token,updated_at=EXCLUDED.updated_at",
            UUID.randomUUID(),employee,session,token,hash,time(now));
    }
    public void unsubscribe(UUID employee, String hash) { jdbc.update("DELETE FROM notification_devices WHERE employee_id=? AND token_hash=?",employee,hash); }
    public int deviceCount(UUID employee) { return jdbc.queryForObject("SELECT count(*) FROM notification_devices WHERE employee_id=?",Integer.class,employee); }
    public boolean owns(UUID employee, String hash) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM notification_devices WHERE employee_id=? AND token_hash=?)",Boolean.class,employee,hash)); }
    public void remove(UUID id) { jdbc.update("DELETE FROM notification_devices WHERE id=?",id); }
    public List<DeviceTarget> devicesForUsers(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        var ids = userIds.stream().distinct().toList();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        return jdbc.query("SELECT id, employee_id, session_id, token FROM notification_devices WHERE employee_id IN (" + placeholders + ")",
            (row, index) -> new DeviceTarget(row.getObject("id", UUID.class), row.getObject("employee_id", UUID.class),
                row.getObject("session_id", UUID.class), row.getString("token")),
            ids.toArray());
    }
    public void enqueue(LocalDate week, String kind, Instant now) {
        jdbc.update("INSERT INTO notification_deliveries(id,device_id,week_start,kind,next_attempt_at) " +
            "SELECT gen_random_uuid(),id,?,?,? FROM notification_devices ON CONFLICT(device_id,week_start,kind) DO NOTHING",week,kind,time(now));
    }
    public Optional<Job> claim(Instant now) {
        var lease = UUID.randomUUID();
        var jobs = jdbc.query("WITH candidate AS (SELECT id FROM notification_deliveries WHERE status='PENDING' AND next_attempt_at<=? " +
            "AND (lease_until IS NULL OR lease_until<?) ORDER BY next_attempt_at,id FOR UPDATE SKIP LOCKED LIMIT 1), " +
            "claimed AS (UPDATE notification_deliveries SET attempts=attempts+1,lease_until=?,lease_token=? WHERE id IN (SELECT id FROM candidate) RETURNING *) " +
            "SELECT c.*,d.employee_id,d.session_id,d.token FROM claimed c JOIN notification_devices d ON d.id=c.device_id",
            (row,index) -> new Job(row.getObject("id",UUID.class),row.getObject("device_id",UUID.class),row.getObject("employee_id",UUID.class),
                row.getObject("session_id",UUID.class),row.getString("token"),row.getObject("week_start",LocalDate.class),row.getString("kind"),row.getInt("attempts"),lease),
            time(now),time(now),time(now.plusSeconds(300)),lease);
        return jobs.stream().findFirst();
    }
    public void finish(Job job, String status, Instant next, String code) {
        jdbc.update("UPDATE notification_deliveries SET status=?,next_attempt_at=?,error_code=?,lease_until=NULL,lease_token=NULL WHERE id=? AND lease_token=?",
            status,time(next),code,job.id(),job.leaseToken());
    }

    public List<String> findTokensByEmployeeId(UUID employeeId) {
        return jdbc.queryForList("SELECT token FROM notification_devices WHERE employee_id = ?", String.class, employeeId);
    }

    public boolean hasReceivedShiftReminder(UUID scheduleId, String kind) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM shift_reminder_deliveries WHERE schedule_id = ? AND kind = ?)",
            Boolean.class, scheduleId, kind));
    }

    public void recordShiftReminder(UUID scheduleId, UUID employeeId, String kind, Instant sentAt) {
        jdbc.update(
            "INSERT INTO shift_reminder_deliveries (id, schedule_id, employee_id, kind, sent_at) VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
            UUID.randomUUID(), scheduleId, employeeId, kind, time(sentAt));
    }

    public void recordNotificationLog(UUID employeeId, String kind, String title, String body, UUID referenceId, Instant sentAt) {
        jdbc.update(
            "INSERT INTO notification_logs (id, employee_id, kind, title, body, reference_id, sent_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), employeeId, kind, title, body, referenceId, time(sentAt));
    }

    public List<NotificationLogEntry> getRecentNotifications(UUID employeeId, int limit) {
        return jdbc.query(
            "SELECT id, employee_id, kind, title, body, reference_id, sent_at FROM notification_logs WHERE employee_id = ? ORDER BY sent_at DESC LIMIT ?",
            (rs, i) -> new NotificationLogEntry(
                rs.getObject("id", UUID.class),
                rs.getObject("employee_id", UUID.class),
                rs.getString("kind"),
                rs.getString("title"),
                rs.getString("body"),
                rs.getObject("reference_id", UUID.class),
                rs.getTimestamp("sent_at").toInstant()
            ),
            employeeId, limit
        );
    }

    private OffsetDateTime time(Instant now) { return OffsetDateTime.ofInstant(now,ZoneOffset.UTC); }
}
