package com.company.hr.attendance.workschedule.domain;

import java.time.*;

public record ShiftWindow(Instant startsAt, Instant endsAt) {
    public static int durationMinutes(LocalTime start, LocalTime end) {
        int minutes = (int) Duration.between(start, end).toMinutes();
        return minutes < 0 ? minutes + 1440 : minutes;
    }
    public static ShiftWindow of(LocalDate date, LocalTime start, LocalTime end, ZoneId zone) {
        var endDate = end.isBefore(start) ? date.plusDays(1) : date;
        return new ShiftWindow(date.atTime(start).atZone(zone).toInstant(),
            endDate.atTime(end).atZone(zone).toInstant());
    }
}
