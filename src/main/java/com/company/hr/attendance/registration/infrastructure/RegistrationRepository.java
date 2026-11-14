package com.company.hr.attendance.registration.infrastructure;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RegistrationRepository {
    public record Grant(UUID id, UUID employeeId, UUID reopenedBy, Instant reopenedAt) {}
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    public RegistrationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; this.named = new NamedParameterJdbcTemplate(jdbc); }
    public List<UUID> ids(UUID employeeId, LocalDate week) {
        return jdbc.query("SELECT shift_template_id FROM shift_registrations WHERE employee_id=? AND week_start=? ORDER BY shift_template_id",
            (row, index) -> row.getObject(1, UUID.class), employeeId, week);
    }
    public Map<UUID, Set<UUID>> selections(LocalDate week, Collection<UUID> employeeIds) {
        if (employeeIds.isEmpty()) return Map.of();
        var result = new HashMap<UUID, Set<UUID>>();
        named.query("SELECT employee_id,shift_template_id FROM shift_registrations WHERE week_start=:week AND employee_id IN (:ids)",
            Map.of("week", week, "ids", employeeIds), (org.springframework.jdbc.core.RowCallbackHandler) row ->
                result.computeIfAbsent(row.getObject("employee_id", UUID.class), ignored -> new HashSet<>())
                    .add(row.getObject("shift_template_id", UUID.class)));
        return result;
    }
    public void replace(UUID employeeId, LocalDate week, Collection<UUID> ids) {
        jdbc.update("DELETE FROM shift_registrations WHERE employee_id=? AND week_start=?", employeeId, week);
        for (var id : ids) jdbc.update("INSERT INTO shift_registrations(id,employee_id,week_start,shift_template_id) VALUES (?,?,?,?)",
            UUID.randomUUID(), employeeId, week, id);
    }
    public Optional<Grant> grant(UUID employeeId, LocalDate week) { return Optional.ofNullable(grants(week, List.of(employeeId)).get(employeeId)); }
    public Map<UUID, Grant> grants(LocalDate week, Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return named.query("SELECT id,employee_id,reopened_by,reopened_at FROM registration_reopens WHERE week_start=:week AND employee_id IN (:ids)",
            Map.of("week",week,"ids",ids), (row,index) -> new Grant(row.getObject("id",UUID.class),row.getObject("employee_id",UUID.class),
                row.getObject("reopened_by",UUID.class),row.getObject("reopened_at",OffsetDateTime.class).toInstant()))
            .stream().collect(Collectors.toMap(Grant::employeeId, grant -> grant));
    }
    public Map<UUID, Integer> counts(LocalDate week, Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        var result = new HashMap<UUID,Integer>();
        named.query("SELECT employee_id,count(*) AS total FROM shift_registrations WHERE week_start=:week AND employee_id IN (:ids) GROUP BY employee_id",
            Map.of("week",week,"ids",ids), (org.springframework.jdbc.core.RowCallbackHandler) row -> result.put(row.getObject("employee_id",UUID.class),row.getInt("total")));
        return result;
    }
    public void reopen(UUID employeeId, LocalDate week, UUID managerId, Instant now) {
        jdbc.update("INSERT INTO registration_reopens(id,employee_id,week_start,reopened_by,reopened_at) VALUES (?,?,?,?,?) " +
            "ON CONFLICT(employee_id,week_start) DO UPDATE SET id=EXCLUDED.id,reopened_by=EXCLUDED.reopened_by,reopened_at=EXCLUDED.reopened_at",
            UUID.randomUUID(),employeeId,week,managerId,OffsetDateTime.ofInstant(now,ZoneOffset.UTC));
    }
    public void close(UUID employeeId, LocalDate week) { jdbc.update("DELETE FROM registration_reopens WHERE employee_id=? AND week_start=?",employeeId,week); }
}
