package com.company.hr.attendance.attendance.api;

import com.company.hr.attendance.attendance.domain.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public final class AttendanceModels {
    private AttendanceModels() {}

    public record CreateAttendanceRequest(
        UUID employeeId,
        UUID scheduleId,
        @NotNull LocalDate workDate,
        @Size(max = 120) String shiftName,
        @NotNull AttendanceType checkType,
        Instant recordedAt,
        @NotBlank @Size(max = 2048) String photoUrl,
        Instant photoCapturedAt,
        @NotBlank @Size(max = 500) String photoLocation,
        @NotBlank @Size(max = 500) String deviceLocation,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double deviceLatitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double deviceLongitude,
        @NotNull AttendanceStatus status,
        @Size(max = 500) String verificationNote
    ) {}

    public record AttendanceRecordResponse(
        UUID id,
        UUID employeeId,
        String employeeName,
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
        public static AttendanceRecordResponse from(AttendanceRecord record, String employeeName) {
            return new AttendanceRecordResponse(
                record.getId(),
                record.getEmployeeId(),
                employeeName,
                record.getScheduleId(),
                record.getWorkDate(),
                record.getShiftName(),
                record.getCheckType(),
                record.getRecordedAt(),
                record.getPhotoUrl(),
                record.getPhotoCapturedAt(),
                record.getPhotoLocation(),
                record.getDeviceLocation(),
                record.getDeviceLatitude(),
                record.getDeviceLongitude(),
                record.getStatus(),
                record.getVerificationNote()
            );
        }
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
        public static <T> PageResponse<T> from(Page<T> result) {
            return new PageResponse<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
            );
        }
    }
}
