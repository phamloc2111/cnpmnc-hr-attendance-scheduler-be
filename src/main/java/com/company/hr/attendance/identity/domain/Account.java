package com.company.hr.attendance.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "identity_accounts")
public class Account {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(nullable = false, length = 100)
    private String passwordHash;
    @Column(nullable = false, length = 120)
    private String displayName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;
    @Column(nullable = false)
    private boolean locked;
    @Column(nullable = false)
    private Instant createdAt;

    @Column(length = 120)
    private String position;
    @Column private UUID positionId;
    @Column
    private UUID managerId;
    @Version
    private long version;
    @ElementCollection
    @CollectionTable(name = "identity_account_skills", joinColumns = @JoinColumn(name = "account_id"))
    @OrderColumn(name = "skill_order")
    @Column(name = "skill", nullable = false, length = 80)
    @BatchSize(size = 100)
    private List<String> skills = new ArrayList<>();

    protected Account() {}

    public Account(String email, String passwordHash, String displayName, Role role) {
        this.id = UUID.randomUUID();
        this.email = email.strip().toLowerCase(Locale.ROOT);
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public Role getRole() { return role; }
    public boolean isLocked() { return locked; }
    public String getPosition() { return position; }
    public UUID getPositionId() { return positionId; }
    public void setPositionId(UUID id) { this.positionId = id; }
    public List<String> getSkills() { return skills; }
    public UUID getManagerId() { return managerId; }
    public long getVersion() { return version; }

    public void updateDetails(String email, String name, Role role, String position, List<String> skills, UUID managerId) {
        this.email = email.strip().toLowerCase(Locale.ROOT);
        this.displayName = name;
        this.role = role;
        this.position = position;
        this.managerId = managerId;
        this.skills.clear();
        this.skills.addAll(skills);
    }
    public void setLocked(boolean locked) { this.locked = locked; }
    public void setRole(Role role) { this.role = role; }
}
