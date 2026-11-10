package com.company.hr.attendance.registration.domain;

import java.time.*;
import java.time.temporal.TemporalAdjusters;

public record RegistrationWindow(Instant opensAt, Instant closesAt) {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static LocalDate nextWeek(Instant now) { return now.atZone(ZONE).toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)); }
    public static RegistrationWindow forWeek(LocalDate monday) {
        return new RegistrationWindow(monday.minusDays(1).atStartOfDay(ZONE).toInstant(), monday.atStartOfDay(ZONE).toInstant());
    }
    public boolean editable(Instant now) { return !now.isBefore(opensAt) && now.isBefore(closesAt); }
}
