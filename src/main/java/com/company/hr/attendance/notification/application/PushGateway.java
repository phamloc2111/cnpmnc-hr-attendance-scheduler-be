package com.company.hr.attendance.notification.application;

import java.time.Instant;
import java.time.LocalDate;

public interface PushGateway {
    enum Result { SENT, INVALID_TOKEN, RETRY }
    boolean available();
    Result send(java.util.UUID employeeId, String token, LocalDate weekStart, String kind, Instant expiresAt, Instant now);
    default Result sendCustom(java.util.UUID userId, String token, String title, String body, String tag, java.util.Map<String, String> data, Instant expiresAt, Instant now) {
        return Result.RETRY;
    }
}
