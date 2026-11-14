package com.company.hr.attendance.notification.infrastructure;

import com.company.hr.attendance.notification.application.ShiftNotificationService;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.notifications.shift-reminder-poll-enabled", havingValue = "true", matchIfMissing = true)
public class ShiftReminderScheduler {
    private final ShiftNotificationService shiftNotifications;

    public ShiftReminderScheduler(ShiftNotificationService shiftNotifications) {
        this.shiftNotifications = shiftNotifications;
    }

    @Scheduled(fixedDelayString = "${app.notifications.poll-delay-ms:15000}", initialDelay = 5000)
    public void run() {
        shiftNotifications.sendShiftReminders(Instant.now());
    }
}
