package com.company.hr.attendance.notification.domain;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.time.format.DateTimeFormatter;

public record ReminderWindow(LocalDate weekStart, Instant closesAt, String kind) {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final List<LocalTime> REMINDER_TIMES = List.of(LocalTime.of(8,0), LocalTime.of(15,0), LocalTime.of(20,0));
    public static Optional<ReminderWindow> at(Instant now) {
        var local = now.atZone(ZONE);
        if (local.getDayOfWeek() != DayOfWeek.SUNDAY) return Optional.empty();
        var week = local.toLocalDate().plusDays(1);
        var kind = "OPEN";
        for (var time : REMINDER_TIMES) {
            if (!local.toLocalTime().isBefore(time)) kind = "REMINDER_" + time.format(DateTimeFormatter.ofPattern("HHmm"));
        }
        return Optional.of(new ReminderWindow(week, week.atStartOfDay(ZONE).toInstant(), kind));
    }
}
