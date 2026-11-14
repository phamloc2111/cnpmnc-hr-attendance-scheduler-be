package com.company.hr.attendance.shiftpass.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shift_pass_notifications")
public class ShiftPassNotification {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID shiftPassId;

    @Column(nullable = false)
    private UUID recipientId;

    @Column(nullable = false, length = 20)
    private String recipientRole;

    @Column(nullable = false, length = 32)
    private String kind;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant closedAt;

    protected ShiftPassNotification() {}

    public ShiftPassNotification(
        UUID shiftPassId,
        UUID recipientId,
        String recipientRole,
        String kind,
        String title,
        String body,
        Instant now
    ) {
        this.id = UUID.randomUUID();
        this.shiftPassId = shiftPassId;
        this.recipientId = recipientId;
        this.recipientRole = recipientRole;
        this.kind = kind;
        this.title = title;
        this.body = body;
        this.status = "OPEN";
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getShiftPassId() { return shiftPassId; }
    public UUID getRecipientId() { return recipientId; }
    public String getRecipientRole() { return recipientRole; }
    public String getKind() { return kind; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getClosedAt() { return closedAt; }

    public void close(Instant now) {
        this.status = "CLOSED";
        this.closedAt = now;
    }

    public void markRead() {
        if ("OPEN".equals(this.status)) {
            this.status = "READ";
        }
    }
}
