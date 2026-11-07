package com.company.hr.attendance.attendance.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attendance_registered_devices")
public class RegisteredDevice {
    @Id
    private UUID employeeId;
    @Column(nullable = false, length = 128)
    private String deviceId;
    @Column(length = 160)
    private String deviceName;
    @Column(nullable = false)
    private Instant registeredAt;
    @Column
    private UUID registeredBy;

    protected RegisteredDevice() {}

    public RegisteredDevice(UUID employeeId, String deviceId, String deviceName, Instant registeredAt, UUID registeredBy) {
        this.employeeId = employeeId;
        this.deviceId = deviceId.strip();
        this.deviceName = deviceName == null || deviceName.isBlank() ? null : deviceName.strip();
        this.registeredAt = registeredAt != null ? registeredAt : Instant.now();
        this.registeredBy = registeredBy;
    }

    public void rebind(String deviceId, String deviceName, UUID registeredBy) {
        this.deviceId = deviceId.strip();
        this.deviceName = deviceName == null || deviceName.isBlank() ? null : deviceName.strip();
        this.registeredAt = Instant.now();
        this.registeredBy = registeredBy;
    }

    public UUID getEmployeeId() { return employeeId; }
    public String getDeviceId() { return deviceId; }
    public String getDeviceName() { return deviceName; }
    public Instant getRegisteredAt() { return registeredAt; }
    public UUID getRegisteredBy() { return registeredBy; }
}
