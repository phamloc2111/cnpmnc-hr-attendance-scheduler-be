package com.company.hr.attendance.notification;
import com.company.hr.attendance.notification.domain.ReminderWindow;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ReminderWindowTests {
    @Test void threeReminderBoundariesAndClosingUseVietnamTime(){
        assertThat(ReminderWindow.at(Instant.parse("2026-11-14T16:59:59Z"))).isEmpty();
        var open=ReminderWindow.at(Instant.parse("2026-11-14T17:00:00Z")).orElseThrow();
        assertThat(open.weekStart()).isEqualTo(LocalDate.of(2026,11,16));assertThat(open.kind()).isEqualTo("OPEN");
        String[] boundaries={"01:00:00", "08:00:00", "13:00:00"};
        String[] kinds={"OPEN", "REMINDER_0800", "REMINDER_1500", "REMINDER_2000"};
        for(int i=0;i<boundaries.length;i++){
            var time=Instant.parse("2026-11-15T"+boundaries[i]+"Z");
            assertThat(ReminderWindow.at(time.minusNanos(1)).orElseThrow().kind()).isEqualTo(kinds[i]);
            assertThat(ReminderWindow.at(time).orElseThrow().kind()).isEqualTo(kinds[i+1]);
        }
        assertThat(ReminderWindow.at(Instant.parse("2026-11-15T16:59:59.999Z")).orElseThrow().kind()).isEqualTo("REMINDER_2000");
        assertThat(ReminderWindow.at(Instant.parse("2026-11-15T17:00:00Z"))).isEmpty();
    }
}
