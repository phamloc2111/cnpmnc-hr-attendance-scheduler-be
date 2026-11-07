package com.company.hr.attendance.attendance.infrastructure;

import com.company.hr.attendance.attendance.api.AttendanceModels.*;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AttendanceEventRepository {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    public AttendanceEventRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
        this.jdbc = jdbc; this.named = named;
    }

    private static final RowMapper<EventResponse> VIEW = (rs, row) -> new EventResponse(
        rs.getObject("id", UUID.class), rs.getObject("schedule_id", UUID.class),
        EventType.valueOf(rs.getString("event_type")), rs.getTimestamp("received_at").toInstant(),
        rs.getTimestamp("located_at").toInstant(), rs.getDouble("latitude"), rs.getDouble("longitude"),
        rs.getDouble("accuracy_meters"), "/api/v1/attendance/events/" + rs.getObject("id", UUID.class) + "/photo");
    private static final String COLUMNS = "e.id, e.schedule_id, e.event_type, e.received_at, e.located_at, e.latitude, e.longitude, e.accuracy_meters";

    public void insert(UUID id, UUID scheduleId, UUID employeeId, EventType type, Instant receivedAt,
                       Instant locatedAt, double latitude, double longitude, double accuracy,
                       String contentType, byte[] photo) {
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO attendance_events (id, schedule_id, employee_id, event_type, received_at,
                    located_at, latitude, longitude, accuracy_meters, photo_content_type, photo_bytes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """);
            statement.setObject(1, id); statement.setObject(2, scheduleId); statement.setObject(3, employeeId);
            statement.setString(4, type.name()); statement.setTimestamp(5, Timestamp.from(receivedAt));
            statement.setTimestamp(6, Timestamp.from(locatedAt)); statement.setDouble(7, latitude);
            statement.setDouble(8, longitude); statement.setDouble(9, accuracy);
            statement.setString(10, contentType); statement.setBytes(11, photo);
            return statement;
        });
    }
    public boolean exists(UUID scheduleId, EventType type) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM attendance_events WHERE schedule_id = ? AND event_type = ?)",
            Boolean.class, scheduleId, type.name()));
    }
    public boolean hasEvents(UUID scheduleId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM attendance_events WHERE schedule_id = ?)", Boolean.class, scheduleId));
    }
    public Optional<EventResponse> find(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM attendance_events e WHERE e.id = ?", VIEW, id)
            .stream().findFirst();
    }
    public UUID employeeId(UUID id) {
        return jdbc.query("SELECT employee_id FROM attendance_events WHERE id = ?",
            (rs, row) -> rs.getObject(1, UUID.class), id).stream().findFirst().orElse(null);
    }
    public Optional<Photo> photo(UUID id) {
        return jdbc.query("SELECT photo_bytes, photo_content_type FROM attendance_events WHERE id = ?",
            (rs, row) -> new Photo(rs.getBytes(1), rs.getString(2)), id).stream().findFirst();
    }
    public List<EventResponse> employeeEvents(UUID employeeId, LocalDate from, LocalDate to) {
        return jdbc.query("SELECT " + COLUMNS + " FROM attendance_events e JOIN work_schedules s ON s.id = e.schedule_id " +
            "WHERE e.employee_id = ? AND s.work_date BETWEEN ? AND ? ORDER BY e.received_at, e.id", VIEW,
            employeeId, Date.valueOf(from), Date.valueOf(to));
    }
    public List<EventResponse> scheduleEvents(List<UUID> scheduleIds) {
        if (scheduleIds.isEmpty()) return List.of();
        return named.query("SELECT " + COLUMNS + " FROM attendance_events e WHERE e.schedule_id IN (:ids)",
            Map.of("ids", scheduleIds), VIEW);
    }
}
