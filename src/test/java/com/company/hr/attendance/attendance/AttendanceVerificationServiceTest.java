package com.company.hr.attendance.attendance;

import com.company.hr.attendance.attendance.api.AttendanceModels.TimemarkEvidence;
import com.company.hr.attendance.attendance.application.AttendanceException;
import com.company.hr.attendance.attendance.application.AttendanceVerificationService;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttendanceVerificationServiceTest {
    private final AttendanceVerificationService verification =
        new AttendanceVerificationService(() -> java.util.Optional.of(new com.company.hr.attendance.organization.WorkplaceApi.Configuration(10.7726, 106.6579, 100, 30, 0, null, null)), 300);
    private final Instant serverTime = Instant.parse("2026-11-07T01:00:00Z");

    @Test
    void verify_acceptsPhotoAndDeviceWithinWorkplaceRadiusWithMatchingTime() {
        verification.verify(10.7727, 106.6579, 5.0,
            evidence(10.77265, 106.65795, serverTime.minusSeconds(120)), serverTime);
    }

    @Test
    void verify_acceptsPhotoOnlyWithinWorkplaceRadiusWithMatchingTime() {
        verification.verify(evidence(10.77265, 106.65795, serverTime.minusSeconds(120)), serverTime);
    }

    @Test
    void verify_rejectsWhenPhotoLocationIsOutOfWorkplaceRadius() {
        assertThatThrownBy(() -> verification.verify(evidence(10.85, 106.6579, serverTime.minusSeconds(60)), serverTime))
            .isInstanceOfSatisfying(AttendanceException.class, error -> {
                assertThat(error.getCode()).isEqualTo("ATTENDANCE_VERIFICATION_FAILED");
                assertThat(error.getStatus().value()).isEqualTo(422);
                assertThat(error.getMessage()).contains("Vị trí trên ảnh Timemark nằm ngoài vùng điểm danh");
            });
    }

    @Test
    void verify_rejectsWhenPhotoTimeDeviatesBeyondThreshold() {
        assertThatThrownBy(() -> verification.verify(evidence(10.7726, 106.6579, serverTime.minusSeconds(301)), serverTime))
            .isInstanceOfSatisfying(AttendanceException.class, error -> {
                assertThat(error.getCode()).isEqualTo("ATTENDANCE_VERIFICATION_FAILED");
                assertThat(error.getStatus().value()).isEqualTo(422);
                assertThat(error.getMessage()).contains("Thời gian trên ảnh Timemark không khớp giờ máy chủ");
            });
    }

    @Test
    void verify_rejectsWithoutPersistingWhenEitherLocationOrTimeFails() {
        assertThatThrownBy(() -> verification.verify(10.80, 106.6579, 5.0,
            evidence(10.7726, 106.6579, serverTime.minusSeconds(301)), serverTime))
            .isInstanceOfSatisfying(AttendanceException.class, error -> {
                assertThat(error.getCode()).isEqualTo("ATTENDANCE_VERIFICATION_FAILED");
                assertThat(error.getStatus().value()).isEqualTo(422);
                assertThat(error.getMessage()).contains("Định vị điện thoại nằm ngoài", "Thời gian trên ảnh Timemark");
            });
    }

    private TimemarkEvidence evidence(double latitude, double longitude, Instant capturedAt) {
        return new TimemarkEvidence(capturedAt, latitude, longitude);
    }
}
