package db.migration;

import java.sql.*;
import java.time.LocalTime;
import java.util.*;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Converts the V4 legacy JDBC TIME representation before adding shift requirements. */
public class V5__shift_requirements extends BaseJavaMigration {
    private record LegacyShift(UUID id, LocalTime start, LocalTime end) {}

    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        try (var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE work_shift_templates ADD COLUMN required_headcount INTEGER NOT NULL DEFAULT 1");
            statement.execute("ALTER TABLE work_shift_templates ADD COLUMN required_position VARCHAR(120)");
            statement.execute("ALTER TABLE work_shift_templates ADD COLUMN overnight BOOLEAN");
        }
        var legacy = new ArrayList<LegacyShift>();
        // Match the old Hibernate TimeJdbcType extractor (hibernate.jdbc.time_zone=UTC)
        // so existing application-visible clock times survive the switch to JDBC LocalTime.
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT id, start_time, end_time FROM work_shift_templates")) {
            while (rows.next()) {
                legacy.add(new LegacyShift(rows.getObject("id", UUID.class),
                    rows.getTime("start_time", utc()).toLocalTime(),
                    rows.getTime("end_time", utc()).toLocalTime()));
            }
        }
        try (var statement = connection.prepareStatement(
            "UPDATE work_shift_templates SET start_time = ?, end_time = ?, overnight = ? WHERE id = ?")) {
            for (var shift : legacy) {
                statement.setObject(1, shift.start());
                statement.setObject(2, shift.end());
                statement.setBoolean(3, shift.end().isBefore(shift.start()));
                statement.setObject(4, shift.id());
                statement.addBatch();
            }
            statement.executeBatch();
        }
        try (var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE work_shift_templates ALTER COLUMN overnight SET NOT NULL");
            statement.execute("ALTER TABLE work_shift_templates ADD CONSTRAINT shift_headcount_positive CHECK (required_headcount >= 1)");
            statement.execute("ALTER TABLE work_shift_templates ADD CONSTRAINT shift_position_not_blank CHECK (required_position IS NULL OR LENGTH(TRIM(required_position)) > 0)");
            statement.execute("ALTER TABLE work_shift_templates ADD CONSTRAINT shift_overnight_matches_times CHECK (overnight = (end_time < start_time))");
        }
    }
    private Calendar utc() { return Calendar.getInstance(TimeZone.getTimeZone("UTC")); }
}
