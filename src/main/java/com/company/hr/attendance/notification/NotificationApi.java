package com.company.hr.attendance.notification;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface NotificationApi {
    /** Sends a best-effort push notification to all registered devices owned by the given users. */
    int pushToUsers(Collection<UUID> userIds, String title, String body, String tag, Map<String, String> data, Instant expiresAt);
}
