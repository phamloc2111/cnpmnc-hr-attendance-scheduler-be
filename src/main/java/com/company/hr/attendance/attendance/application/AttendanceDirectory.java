package com.company.hr.attendance.attendance.application;

import com.company.hr.attendance.attendance.AttendanceApi;
import com.company.hr.attendance.attendance.infrastructure.AttendanceEventRepository;
import com.company.hr.attendance.attendance.infrastructure.AttendanceExplanationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AttendanceDirectory implements AttendanceApi {
    private final AttendanceEventRepository events;
    private final AttendanceExplanationRepository explanations;
    public AttendanceDirectory(AttendanceEventRepository events, AttendanceExplanationRepository explanations) { this.events = events; this.explanations = explanations; }
    @Override public java.util.List<CheckIn> checkIns(java.util.List<UUID> scheduleIds) {
        return events.scheduleEvents(scheduleIds).stream()
            .filter(event -> event.type() == com.company.hr.attendance.attendance.api.AttendanceModels.EventType.CHECK_IN)
            .map(event -> new CheckIn(event.scheduleId(), event.receivedAt())).toList();
    }
    @Override public java.util.List<Event> events(java.util.List<UUID> scheduleIds) {
        return events.scheduleEvents(scheduleIds).stream().map(event -> new Event(event.scheduleId(),event.type().name(),event.receivedAt())).toList();
    }
    @Override public java.util.List<ApprovedExplanation> approvedExplanations(java.util.List<UUID> scheduleIds) {
        return explanations.forSchedules(scheduleIds).stream()
            .filter(row->row.status()==com.company.hr.attendance.attendance.domain.AttendanceExplanation.Status.APPROVED)
            .map(row->new ApprovedExplanation(row.scheduleId(),row.employeeId(),row.workDate(),row.category().name(),row.submittedAt(),row.exemptLatePenalty())).toList();
    }
    @Override public boolean hasEvents(UUID scheduleId) { return events.hasEvents(scheduleId); }
}
