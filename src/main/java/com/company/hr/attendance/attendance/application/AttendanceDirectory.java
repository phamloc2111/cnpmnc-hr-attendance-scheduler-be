package com.company.hr.attendance.attendance.application;

import com.company.hr.attendance.attendance.AttendanceApi;
import com.company.hr.attendance.attendance.infrastructure.AttendanceEventRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AttendanceDirectory implements AttendanceApi {
    private final AttendanceEventRepository events;
    public AttendanceDirectory(AttendanceEventRepository events) { this.events = events; }
    @Override public boolean hasEvents(UUID scheduleId) { return events.hasEvents(scheduleId); }
}
