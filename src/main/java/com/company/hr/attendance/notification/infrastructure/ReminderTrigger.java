package com.company.hr.attendance.notification.infrastructure;

import com.company.hr.attendance.notification.application.RegistrationReminderService;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

@Component
public class ReminderTrigger {
    private final RegistrationReminderService reminders;
    private final AtomicBoolean running=new AtomicBoolean();
    private final ExecutorService executor=Executors.newSingleThreadExecutor(Thread.ofVirtual().name("registration-reminder-cron").factory());
    public ReminderTrigger(RegistrationReminderService reminders) { this.reminders=reminders; }
    public boolean trigger() {
        if (!running.compareAndSet(false,true)) return false;
        executor.execute(()->{try{reminders.run();}finally{running.set(false);}});
        return true;
    }
    @PreDestroy void close(){executor.shutdownNow();}
}
