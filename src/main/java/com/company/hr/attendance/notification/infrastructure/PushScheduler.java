package com.company.hr.attendance.notification.infrastructure;

import com.company.hr.attendance.notification.application.RegistrationReminderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;


@Configuration
@EnableScheduling
@ConditionalOnProperty(name="app.notifications.enabled",havingValue="true")
public class PushScheduler {
    private final RegistrationReminderService reminders;
    public PushScheduler(RegistrationReminderService reminders) { this.reminders=reminders; }
    @Scheduled(fixedDelayString="${app.notifications.poll-delay-ms:60000}",initialDelay=10000)
    public void run() { reminders.run(); }
}
