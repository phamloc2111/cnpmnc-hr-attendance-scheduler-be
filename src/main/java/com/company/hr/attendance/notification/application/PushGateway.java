package com.company.hr.attendance.notification.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public interface PushGateway {
    enum Result { SENT, INVALID_TOKEN, RETRY }
    boolean available();
    Result send(UUID employeeId, String token, LocalDate weekStart, String kind, Instant expiresAt, Instant now);
    default Result sendCustom(UUID employeeId, String token, String title, String body, Map<String, String> data, Instant expiresAt, Instant now) {
        return send(employeeId, token, LocalDate.now(), "CUSTOM", expiresAt, now);
    }
}
