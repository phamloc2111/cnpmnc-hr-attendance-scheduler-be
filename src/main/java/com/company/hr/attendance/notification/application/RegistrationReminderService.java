package com.company.hr.attendance.notification.application;

import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.registration.RegistrationApi;
import com.company.hr.attendance.notification.domain.ReminderWindow;
import com.company.hr.attendance.notification.infrastructure.PushRepository;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RegistrationReminderService {
    private final PushRepository repository;
    private final PushGateway gateway;
    private final IdentityApi identity;
    private final RegistrationApi registrations;
    private final LocalTime reminderTime;
    private final Clock clock;
    public RegistrationReminderService(PushRepository repository, PushGateway gateway, IdentityApi identity, RegistrationApi registrations,
        @Value("${app.notifications.reminder-time:22:00}") String reminderTime, @org.springframework.beans.factory.annotation.Qualifier("notificationClock") Clock clock) {
        this.repository=repository;this.gateway=gateway;this.identity=identity;this.registrations=registrations;this.reminderTime=LocalTime.parse(reminderTime);this.clock=clock;
        if (!this.reminderTime.isAfter(LocalTime.MIDNIGHT)) throw new IllegalArgumentException("PUSH_REMINDER_TIME must be after 00:00 and before 24:00");
    }
    public void run() {
        if (!gateway.available()) return;
        var start=clock.instant();
        ReminderWindow.at(start,reminderTime).ifPresent(value -> repository.enqueue(value.weekStart(),value.kind(),start));
        for (int count=0;count<100;count++) {
            var now=clock.instant();var window=ReminderWindow.at(now,reminderTime);
            var next=repository.claim(now);if(next.isEmpty())break;var job=next.get();
            var close=job.weekStart().atStartOfDay(ReminderWindow.ZONE).toInstant();
            if (!now.isBefore(close) || window.isEmpty() || !window.get().weekStart().equals(job.weekStart())
                || !identity.canReceiveRegistrationReminders(job.employeeId(),job.sessionId())
                || (job.kind().equals("REMINDER") && registrations.hasRegistered(job.employeeId(),job.weekStart()))) {
                repository.finish(job,"SKIPPED",now,"NOT_ELIGIBLE");continue;
            }
            PushGateway.Result result;
            try { result=gateway.send(job.employeeId(),job.token(),job.weekStart(),job.kind(),close,now); }
            catch (RuntimeException error) { result=PushGateway.Result.RETRY; }
            switch(result) {
                case SENT -> repository.finish(job,"SENT",now,null);
                case INVALID_TOKEN -> repository.remove(job.deviceId());
                case RETRY -> repository.finish(job,job.attempts()>=5?"FAILED":"PENDING",now.plusSeconds(Math.min(1800,60L<<(job.attempts()-1))),"PROVIDER_RETRY");
            }
        }
    }
}
