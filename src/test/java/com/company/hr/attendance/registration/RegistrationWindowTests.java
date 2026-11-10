package com.company.hr.attendance.registration;
import com.company.hr.attendance.registration.domain.RegistrationWindow;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RegistrationWindowTests {
    @Test void sundayWindowUsesVietnamTimeAndIncludesTheEntireLastMinute() {
        var week = LocalDate.of(2026,11,16); var window = RegistrationWindow.forWeek(week);
        assertThat(window.opensAt()).isEqualTo(Instant.parse("2026-11-14T17:00:00Z"));
        assertThat(window.closesAt()).isEqualTo(Instant.parse("2026-11-15T17:00:00Z"));
        assertThat(window.editable(window.opensAt().minusNanos(1))).isFalse();
        assertThat(window.editable(window.opensAt())).isTrue();
        assertThat(window.editable(window.closesAt().minusNanos(1))).isTrue();
        assertThat(window.editable(window.closesAt())).isFalse();
        assertThat(RegistrationWindow.nextWeek(window.opensAt())).isEqualTo(week);
        assertThat(RegistrationWindow.nextWeek(window.closesAt())).isEqualTo(week.plusDays(7));
    }
}
