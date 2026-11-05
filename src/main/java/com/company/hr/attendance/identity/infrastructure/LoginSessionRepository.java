package com.company.hr.attendance.identity.infrastructure;

import com.company.hr.attendance.identity.domain.LoginSession;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginSessionRepository extends JpaRepository<LoginSession, UUID> {
    void deleteByExpiresAtBefore(Instant now);
    void deleteByAccountId(UUID accountId);
}
