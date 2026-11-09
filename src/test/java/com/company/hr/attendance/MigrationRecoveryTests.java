package com.company.hr.attendance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

class MigrationRecoveryTests {
    @Test void reconstructedMigrationAndGuardedHistoryRecoveryPreserveExistingData() throws Exception {
        try (var postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            var configuration = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            var original = configuration.target("10").load();
            original.migrate();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                var recovery = Files.readString(Path.of("scripts/recover-v10-history.sql"));
                execute(connection, "INSERT INTO registration_reopens VALUES ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '2026-11-09', '00000000-0000-0000-0000-000000000003', now())");
                var before = scalar(connection, "SELECT row_to_json(r)::text FROM registration_reopens r");
                assertThat(scalar(connection, "SELECT checksum::text FROM flyway_schema_history WHERE version='10'"))
                    .isEqualTo("421065846");
                execute(connection, "UPDATE flyway_schema_history SET checksum=-1798121770 WHERE version='10'");
                assertThat(original.validateWithResult().validationSuccessful).isFalse();

                execute(connection, "ALTER TABLE registration_reopens ADD COLUMN unexpected TEXT");
                assertThatThrownBy(() -> execute(connection, recovery)).hasMessageContaining("Registration schema differs");
                assertThat(scalar(connection, "SELECT checksum::text FROM flyway_schema_history WHERE version='10'"))
                    .isEqualTo("-1798121770");
                execute(connection, "ALTER TABLE registration_reopens DROP COLUMN unexpected");

                execute(connection, recovery);
                original.validate();
                assertThat(scalar(connection, "SELECT row_to_json(r)::text FROM registration_reopens r")).isEqualTo(before);
                execute(connection, recovery); // Safe to rerun after successful recovery.

                execute(connection, "UPDATE flyway_schema_history SET checksum=123 WHERE version='10'");
                assertThatThrownBy(() -> execute(connection, recovery)).hasMessageContaining("Unexpected V10 history");
                assertThat(scalar(connection, "SELECT checksum::text FROM flyway_schema_history WHERE version='10'"))
                    .isEqualTo("123");
                execute(connection, "UPDATE flyway_schema_history SET checksum=421065846 WHERE version='10'");

                var latest = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load();
                latest.migrate();
                latest.validate();
                assertThat(scalar(connection, "SELECT to_regclass('organization_workplace_settings')::text"))
                    .isEqualTo("organization_workplace_settings");
                assertThat(scalar(connection, "SELECT row_to_json(r)::text FROM registration_reopens r")).isEqualTo(before);
            }
        }
    }
    private static void execute(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }
    private static String scalar(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue(); return rows.getString(1);
        }
    }
}
