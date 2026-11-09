package com.company.hr.attendance.organization.infrastructure;

import com.company.hr.attendance.organization.WorkplaceApi.Configuration;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkplaceRepository {
    private final JdbcTemplate jdbc;
    public WorkplaceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<Configuration> find() {
        return jdbc.query("SELECT * FROM organization_workplace_settings WHERE id = 1", (row, index) ->
            new Configuration(row.getDouble("latitude"), row.getDouble("longitude"), row.getInt("radius_meters"),
                row.getInt("gps_tolerance_meters"), row.getLong("version"), row.getTimestamp("updated_at").toInstant(),
                row.getObject("updated_by", UUID.class))).stream().findFirst();
    }
    public boolean save(double latitude, double longitude, int radius, int tolerance, long version, UUID actor, Instant now) {
        if (version == 0) {
            return jdbc.update("""
                INSERT INTO organization_workplace_settings
                    (id, latitude, longitude, radius_meters, gps_tolerance_meters, version, updated_at, updated_by)
                VALUES (1, ?, ?, ?, ?, 1, ?, ?) ON CONFLICT (id) DO NOTHING
                """, latitude, longitude, radius, tolerance, Timestamp.from(now), actor) == 1;
        }
        return jdbc.update("""
            UPDATE organization_workplace_settings SET latitude = ?, longitude = ?, radius_meters = ?,
                gps_tolerance_meters = ?, version = version + 1, updated_at = ?, updated_by = ?
            WHERE id = 1 AND version = ?
            """, latitude, longitude, radius, tolerance, Timestamp.from(now), actor, version) == 1;
    }
}
