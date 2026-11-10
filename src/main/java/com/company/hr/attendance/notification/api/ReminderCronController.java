package com.company.hr.attendance.notification.api;

import com.company.hr.attendance.notification.application.PushGateway;
import com.company.hr.attendance.notification.infrastructure.ReminderTrigger;
import com.company.hr.attendance.shared.api.ApiError;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notification-jobs/registration-reminders")
public class ReminderCronController {
    private final String secret;
    private final PushGateway gateway;
    private final ReminderTrigger trigger;
    public ReminderCronController(@Value("${app.notifications.cron-secret:}") String secret,PushGateway gateway,ReminderTrigger trigger) {
        this.secret=secret;this.gateway=gateway;this.trigger=trigger;
    }
    @PostMapping public ResponseEntity<?> run(@RequestHeader(value="X-Reminder-Cron-Key",required=false) String key) {
        if(secret.length()<32 || key==null || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),key.getBytes(StandardCharsets.UTF_8)))
            return ResponseEntity.status(401).cacheControl(CacheControl.noStore()).body(ApiError.of("CRON_UNAUTHORIZED","Khóa cron không hợp lệ.","/api/v1/notification-jobs/registration-reminders"));
        if(!gateway.available())return ResponseEntity.status(503).cacheControl(CacheControl.noStore()).body(ApiError.of("PUSH_NOT_CONFIGURED","Thông báo đẩy chưa được bật.","/api/v1/notification-jobs/registration-reminders"));
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(Map.of("queued",trigger.trigger()));
    }
}
