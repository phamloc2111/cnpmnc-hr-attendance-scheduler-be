package com.company.hr.attendance.attendance;

import java.util.UUID;

/** Public boundary for schedule operations affected by recorded attendance. */
public interface AttendanceApi {
    record CheckIn(UUID scheduleId, java.time.Instant receivedAt) {}
    java.util.List<CheckIn> checkIns(java.util.List<UUID> scheduleIds);
    boolean hasEvents(UUID scheduleId);
}
