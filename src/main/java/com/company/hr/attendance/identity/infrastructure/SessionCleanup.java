package com.company.hr.attendance.identity.infrastructure;

import java.time.Instant;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@EnableScheduling
public class SessionCleanup {
    private final LoginSessionRepository sessions;

    public SessionCleanup(LoginSessionRepository sessions) { this.sessions = sessions; }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1H")
    @Transactional
    public void removeExpiredSessions() { sessions.deleteByExpiresAtBefore(Instant.now()); }
}
