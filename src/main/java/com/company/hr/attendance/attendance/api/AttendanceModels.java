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
    public record TimemarkEvidence(Instant capturedAt, double latitude, double longitude) {}
    /** Data visibly read from the Timemark overlay in an uploaded image. */
    public record TimemarkAnalysisResponse(
        boolean readable,
        String printedDateTime,
        Double latitude,
        Double longitude,
        String address,
        String reason
    ) {}
}
