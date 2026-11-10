package com.company.hr.attendance.notification.domain;

import java.time.*;
import java.util.Optional;

public record ReminderWindow(LocalDate weekStart, Instant closesAt, String kind) {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static Optional<ReminderWindow> at(Instant now, LocalTime reminderTime) {
        var local = now.atZone(ZONE);
        if (local.getDayOfWeek() != DayOfWeek.SUNDAY) return Optional.empty();
        var week = local.toLocalDate().plusDays(1);
        return Optional.of(new ReminderWindow(week, week.atStartOfDay(ZONE).toInstant(),
            local.toLocalTime().isBefore(reminderTime) ? "OPEN" : "REMINDER"));
    }
}
