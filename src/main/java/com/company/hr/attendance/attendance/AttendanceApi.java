package com.company.hr.attendance.attendance;

import java.util.UUID;

/** Public boundary for schedule operations affected by recorded attendance. */
public interface AttendanceApi {
    boolean hasEvents(UUID scheduleId);
}
