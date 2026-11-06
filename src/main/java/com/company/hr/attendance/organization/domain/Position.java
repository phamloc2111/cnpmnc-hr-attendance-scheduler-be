package com.company.hr.attendance.organization.domain;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "organization_positions")
public class Position {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, unique = true, length = 240) private String normalizedName;
    @Column(nullable = false) private boolean active;
    @Column private UUID createdBy;
    @Version private long version;
    protected Position() {}
    public Position(String name, UUID creator) {
        id = UUID.randomUUID(); this.name = PositionName.display(name);
        normalizedName = PositionName.key(name); active = true; createdBy = creator;
    }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }
    public void setActive(boolean active) { this.active = active; }
}
