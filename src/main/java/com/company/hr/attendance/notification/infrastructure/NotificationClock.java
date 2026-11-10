package com.company.hr.attendance.notification.infrastructure;
import java.time.Clock;
import org.springframework.context.annotation.*;
@Configuration("notificationTimeConfiguration")
public class NotificationClock { @Bean("notificationClock") public Clock notificationClock() { return Clock.systemUTC(); } }
