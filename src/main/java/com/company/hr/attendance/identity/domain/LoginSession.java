package com.company.hr.attendance.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identity_sessions")
public class LoginSession {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID accountId;
    @Column(nullable = false)
    private Instant expiresAt;

    protected LoginSession() {}

    public LoginSession(UUID accountId, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public Instant getExpiresAt() { return expiresAt; }
}
