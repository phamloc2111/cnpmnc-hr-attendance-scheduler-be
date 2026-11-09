package com.company.hr.attendance.attendance;

import com.company.hr.attendance.attendance.api.AttendanceModels.TimemarkEvidence;
import com.company.hr.attendance.attendance.application.AttendanceException;
import com.company.hr.attendance.attendance.application.AttendanceVerificationService;
import com.company.hr.attendance.organization.WorkplaceApi;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WorkplaceVerificationTest {
    private final Instant now = Instant.parse("2026-11-09T03:00:00Z");
    private final AttendanceVerificationService service = new AttendanceVerificationService(
        () -> Optional.of(new WorkplaceApi.Configuration(0, 0, 100, 30, 1, now, null)), 300);
    double north(double meters) { return Math.toDegrees(meters / 6_371_000); }
    TimemarkEvidence photo(double meters) { return new TimemarkEvidence(now, north(meters), 0); }
    @Test void acceptsRadiusPlusConfiguredToleranceIncludingBoundary() {
        assertThatCode(() -> service.verify(north(130), 0.0, 30.0, photo(130), now)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.verify(north(130.1), 0.0, 5.0, photo(0), now))
            .isInstanceOfSatisfying(AttendanceException.class, error -> assertThat(error.getCode()).isEqualTo("ATTENDANCE_VERIFICATION_FAILED"));
        assertThatThrownBy(() -> service.verify(0.0, 0.0, 5.0, photo(131), now)).isInstanceOf(AttendanceException.class);
    }
    @Test void poorAccuracyCannotExpandTheAllowedRadius() {
        assertThatThrownBy(() -> service.verify(north(120), 0.0, 30.1, photo(120), now))
            .isInstanceOfSatisfying(AttendanceException.class, error -> assertThat(error.getCode()).isEqualTo("GPS_ACCURACY_TOO_LOW"));
        assertThatThrownBy(() -> service.verify(north(1000), 0.0, 5000.0, photo(1000), now)).isInstanceOf(AttendanceException.class);
    }
    @Test void invalidPhotoCoordinatesCannotBypassDistanceChecks() {
        assertThatThrownBy(() -> service.verify(0.0, 0.0, 5.0, new TimemarkEvidence(now, Double.NaN, 0), now)).isInstanceOf(AttendanceException.class);
        assertThatThrownBy(() -> service.verify(0.0, 0.0, 5.0, new TimemarkEvidence(now, 91, 0), now)).isInstanceOf(AttendanceException.class);
    }
}
