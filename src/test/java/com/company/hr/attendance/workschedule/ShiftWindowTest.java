package com.company.hr.attendance.workschedule;

import com.company.hr.attendance.workschedule.domain.ShiftWindow;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ShiftWindowTest {
    @Test void window_overnightMonthBoundary_convertsToUtc() {
        var window = ShiftWindow.of(LocalDate.of(2026, 11, 30), LocalTime.of(22, 0), LocalTime.of(6, 0), ZoneId.of("Asia/Ho_Chi_Minh"));
        assertThat(window.startsAt()).isEqualTo(Instant.parse("2026-11-30T15:00:00Z"));
        assertThat(window.endsAt()).isEqualTo(Instant.parse("2026-11-30T23:00:00Z"));
        assertThat(Duration.between(window.startsAt(), window.endsAt()).toMinutes()).isEqualTo(480);
    }
    @Test void window_earlyMorning_startsOnPreviousUtcDate() {
        var window = ShiftWindow.of(LocalDate.of(2026, 11, 1), LocalTime.of(0, 0), LocalTime.of(6, 0), ZoneId.of("Asia/Ho_Chi_Minh"));
        assertThat(window.startsAt()).isEqualTo(Instant.parse("2026-10-31T17:00:00Z"));
        assertThat(window.endsAt()).isEqualTo(Instant.parse("2026-10-31T23:00:00Z"));
    }
    @Test void duration_dayAndNight_returnsMinutes() {
        assertThat(ShiftWindow.durationMinutes(LocalTime.of(8, 0), LocalTime.of(17, 0))).isEqualTo(540);
        assertThat(ShiftWindow.durationMinutes(LocalTime.of(22, 0), LocalTime.of(6, 0))).isEqualTo(480);
    }
}
