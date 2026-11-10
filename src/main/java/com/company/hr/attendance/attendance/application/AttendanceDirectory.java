package com.company.hr.attendance.attendance.application;

import com.company.hr.attendance.attendance.AttendanceApi;
import com.company.hr.attendance.attendance.infrastructure.AttendanceEventRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AttendanceDirectory implements AttendanceApi {
    private final AttendanceEventRepository events;
    public AttendanceDirectory(AttendanceEventRepository events) { this.events = events; }
    @Override public java.util.List<CheckIn> checkIns(java.util.List<UUID> scheduleIds) {
        return events.scheduleEvents(scheduleIds).stream()
            .filter(event -> event.type() == com.company.hr.attendance.attendance.api.AttendanceModels.EventType.CHECK_IN)
            .map(event -> new CheckIn(event.scheduleId(), event.receivedAt())).toList();
    }
    @Override public boolean hasEvents(UUID scheduleId) { return events.hasEvents(scheduleId); }
}
