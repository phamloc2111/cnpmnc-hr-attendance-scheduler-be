package com.company.hr.attendance.attendance.api;

import java.time.Instant;
import java.util.UUID;

public final class AttendanceModels {
    private AttendanceModels() {}
    public enum EventType { CHECK_IN, CHECK_OUT }
    public record EventResponse(UUID id, UUID scheduleId, EventType type, Instant receivedAt,
                                Instant locatedAt, double latitude, double longitude, double accuracyMeters,
                                String photoUrl) {}
    public record Photo(byte[] bytes, String contentType) {}
}
