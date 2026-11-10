package com.company.hr.attendance.notification.application;

import java.time.Instant;
import java.time.LocalDate;

public interface PushGateway {
    enum Result { SENT, INVALID_TOKEN, RETRY }
    boolean available();
    Result send(java.util.UUID employeeId, String token, LocalDate weekStart, String kind, Instant expiresAt, Instant now);
}
