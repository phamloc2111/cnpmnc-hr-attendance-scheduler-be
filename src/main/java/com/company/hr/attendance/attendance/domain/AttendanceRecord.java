package com.company.hr.attendance.attendance.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "attendance_records")
public class AttendanceRecord {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID employeeId;
    @Column
    private UUID scheduleId;
    @Column(nullable = false)
    private LocalDate workDate;
    @Column(length = 120)
    private String shiftName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceType checkType;
    @Column(nullable = false)
    private Instant recordedAt;
    @Column(nullable = false, length = 2048)
    private String photoUrl;
    @Column
    private Instant photoCapturedAt;
    @Column(nullable = false, length = 500)
    private String photoLocation;
    @Column(nullable = false, length = 500)
    private String deviceLocation;
    @Column
    private Double deviceLatitude;
    @Column
    private Double deviceLongitude;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AttendanceStatus status;
    @Column(length = 500)
    private String verificationNote;

    protected AttendanceRecord() {}

    public AttendanceRecord(
        UUID employeeId,
        UUID scheduleId,
        LocalDate workDate,
        String shiftName,
        AttendanceType checkType,
        Instant recordedAt,
        String photoUrl,
        Instant photoCapturedAt,
        String photoLocation,
        String deviceLocation,
        Double deviceLatitude,
        Double deviceLongitude,
        AttendanceStatus status,
        String verificationNote
    ) {
        this(UUID.randomUUID(), employeeId, scheduleId, workDate, shiftName, checkType, recordedAt,
            photoUrl, photoCapturedAt, photoLocation, deviceLocation, deviceLatitude, deviceLongitude,
            status, verificationNote);
    }

    public AttendanceRecord(
        UUID id,
        UUID employeeId,
        UUID scheduleId,
        LocalDate workDate,
        String shiftName,
        AttendanceType checkType,
        Instant recordedAt,
        String photoUrl,
        Instant photoCapturedAt,
        String photoLocation,
        String deviceLocation,
        Double deviceLatitude,
        Double deviceLongitude,
        AttendanceStatus status,
        String verificationNote
    ) {
        this.id = id;
        this.employeeId = employeeId;
        this.scheduleId = scheduleId;
        this.workDate = workDate;
        this.shiftName = shiftName == null || shiftName.isBlank() ? null : shiftName.strip();
        this.checkType = checkType;
        this.recordedAt = recordedAt != null ? recordedAt : Instant.now();
        this.photoUrl = photoUrl.strip();
        this.photoCapturedAt = photoCapturedAt;
        this.photoLocation = photoLocation.strip();
        this.deviceLocation = deviceLocation.strip();
        this.deviceLatitude = deviceLatitude;
        this.deviceLongitude = deviceLongitude;
        this.status = status;
        this.verificationNote = verificationNote == null || verificationNote.isBlank() ? null : verificationNote.strip();
    }

    public UUID getId() { return id; }
    public UUID getEmployeeId() { return employeeId; }
    public UUID getScheduleId() { return scheduleId; }
    public LocalDate getWorkDate() { return workDate; }
    public String getShiftName() { return shiftName; }
    public AttendanceType getCheckType() { return checkType; }
    public Instant getRecordedAt() { return recordedAt; }
    public String getPhotoUrl() { return photoUrl; }
    public Instant getPhotoCapturedAt() { return photoCapturedAt; }
    public String getPositionOnPhoto() { return photoLocation; }
    public String getPhotoLocation() { return photoLocation; }
    public String getDeviceLocation() { return deviceLocation; }
    public Double getDeviceLatitude() { return deviceLatitude; }
    public Double getDeviceLongitude() { return deviceLongitude; }
    public AttendanceStatus getStatus() { return status; }
    public String getVerificationNote() { return verificationNote; }
}
