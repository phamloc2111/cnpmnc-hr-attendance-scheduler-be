package com.company.hr.attendance.notification;
import com.company.hr.attendance.notification.domain.ReminderWindow;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ReminderWindowTests {
    @Test void openingReminderAndClosingUseVietnamTimeRatherThanUtcWeekday(){
        var at=LocalTime.of(22,0);
        assertThat(ReminderWindow.at(Instant.parse("2026-11-14T16:59:59Z"),at)).isEmpty();
        var open=ReminderWindow.at(Instant.parse("2026-11-14T17:00:00Z"),at).orElseThrow();
        assertThat(open.weekStart()).isEqualTo(LocalDate.of(2026,11,16));assertThat(open.kind()).isEqualTo("OPEN");
        assertThat(ReminderWindow.at(Instant.parse("2026-11-15T15:00:00Z"),at).orElseThrow().kind()).isEqualTo("REMINDER");
        assertThat(ReminderWindow.at(Instant.parse("2026-11-15T16:59:59.999Z"),at)).isPresent();
        assertThat(ReminderWindow.at(Instant.parse("2026-11-15T17:00:00Z"),at)).isEmpty();
    }
}
