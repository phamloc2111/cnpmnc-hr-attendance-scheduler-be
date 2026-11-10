package com.company.hr.attendance.registration.infrastructure;

import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RegistrationRepository {
    private final JdbcTemplate jdbc;
    public RegistrationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<UUID> ids(UUID employeeId, LocalDate week) {
        return jdbc.query("SELECT shift_template_id FROM shift_registrations WHERE employee_id=? AND week_start=? ORDER BY shift_template_id",
            (row, index) -> row.getObject(1, UUID.class), employeeId, week);
    }
    public void replace(UUID employeeId, LocalDate week, Collection<UUID> ids) {
        jdbc.update("DELETE FROM shift_registrations WHERE employee_id=? AND week_start=?", employeeId, week);
        for (var id : ids) jdbc.update("INSERT INTO shift_registrations(id,employee_id,week_start,shift_template_id) VALUES (?,?,?,?)",
            UUID.randomUUID(), employeeId, week, id);
    }
}
