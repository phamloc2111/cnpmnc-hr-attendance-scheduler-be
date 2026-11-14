package com.company.hr.attendance.notification.api;

import com.company.hr.attendance.notification.application.PushGateway;
import com.company.hr.attendance.notification.application.ShiftNotificationService;
import com.company.hr.attendance.shared.api.ApiError;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notification-jobs/shift-reminders")
public class ShiftReminderCronController {
    private final String secret;
    private final PushGateway gateway;
    private final ShiftNotificationService shiftNotificationService;

    public ShiftReminderCronController(
        @Value("${app.notifications.cron-secret:}") String secret,
        PushGateway gateway,
        ShiftNotificationService shiftNotificationService
    ) {
        this.secret = secret;
        this.gateway = gateway;
        this.shiftNotificationService = shiftNotificationService;
    }

    @PostMapping
    public ResponseEntity<?> run(@RequestHeader(value = "X-Reminder-Cron-Key", required = false) String key) {
        if (secret.length() < 32 || key == null || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(401).cacheControl(CacheControl.noStore())
                .body(ApiError.of("CRON_UNAUTHORIZED", "Khóa cron không hợp lệ.", "/api/v1/notification-jobs/shift-reminders"));
        }
        int count = shiftNotificationService.sendShiftReminders(Instant.now());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(Map.of("queued", count));
    }

    @Scheduled(fixedRate = 300000)
    public void scheduledRun() {
        if (gateway.available()) {
            shiftNotificationService.sendShiftReminders(Instant.now());
        }
    }
}
