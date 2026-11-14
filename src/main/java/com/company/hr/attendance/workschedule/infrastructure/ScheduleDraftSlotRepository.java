package com.company.hr.attendance.workschedule.infrastructure;

import com.company.hr.attendance.workschedule.api.ScheduleModels.DraftSlot;
import java.time.LocalDate;
import java.sql.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ScheduleDraftSlotRepository {
    private final JdbcTemplate jdbc;

    public ScheduleDraftSlotRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void replace(UUID managerId, LocalDate weekStart, List<DraftSlot> slots) {
        jdbc.update("DELETE FROM schedule_draft_selected_slots WHERE manager_id=? AND week_start=?", managerId, weekStart);
        jdbc.batchUpdate("INSERT INTO schedule_draft_selected_slots(manager_id,week_start,work_date,shift_template_id) "
                + "VALUES (?,?,?,?)", slots, 100, (statement, slot) -> {
            statement.setObject(1, managerId);
            statement.setDate(2, Date.valueOf(weekStart));
            statement.setDate(3, Date.valueOf(slot.workDate()));
            statement.setObject(4, slot.shiftTemplateId());
        });
    }

    public List<DraftSlot> find(UUID managerId, LocalDate weekStart) {
        return jdbc.query("SELECT work_date,shift_template_id FROM schedule_draft_selected_slots "
                + "WHERE manager_id=? AND week_start=? ORDER BY work_date,shift_template_id",
            (rs, row) -> new DraftSlot(rs.getObject("work_date", LocalDate.class),
                rs.getObject("shift_template_id", UUID.class)), managerId, weekStart);
    }
}
