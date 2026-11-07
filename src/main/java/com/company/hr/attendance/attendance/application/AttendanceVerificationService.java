package com.company.hr.attendance.attendance.application;

import com.company.hr.attendance.attendance.api.AttendanceModels.TimemarkEvidence;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Fast, local second-layer validation after the separate Timemark AI extraction. */
@Service
public class AttendanceVerificationService {
    private final Double workplaceLatitude;
    private final Double workplaceLongitude;
    private final double radiusMeters;
    private final long maxTimeDifferenceSeconds;

    public AttendanceVerificationService(
        @Value("${app.attendance.verification.workplace-latitude:}") Double workplaceLatitude,
        @Value("${app.attendance.verification.workplace-longitude:}") Double workplaceLongitude,
        @Value("${app.attendance.verification.radius-meters:100}") double radiusMeters,
        @Value("${app.attendance.verification.max-time-difference-seconds:300}") long maxTimeDifferenceSeconds
    ) {
        this.workplaceLatitude = workplaceLatitude;
        this.workplaceLongitude = workplaceLongitude;
        this.radiusMeters = radiusMeters;
        this.maxTimeDifferenceSeconds = maxTimeDifferenceSeconds;
    }

    public void verify(Double deviceLatitude, Double deviceLongitude, TimemarkEvidence timemark, Instant serverTime) {
        if (workplaceLatitude == null || workplaceLongitude == null || radiusMeters <= 0 || maxTimeDifferenceSeconds < 0) {
            throw new AttendanceException(HttpStatus.SERVICE_UNAVAILABLE, "WORKPLACE_NOT_CONFIGURED",
                "Chưa cấu hình tọa độ hoặc bán kính điểm làm việc. Vui lòng liên hệ quản lý.");
        }
        var reasons = new ArrayList<String>();
        validateCoordinates(deviceLatitude, deviceLongitude, "Định vị điện thoại", reasons);
        if (timemark == null) {
            reasons.add("Không có thời gian đọc từ ảnh Timemark");
        } else {
            validateCoordinates(timemark.latitude(), timemark.longitude(), "Vị trí trên ảnh Timemark", reasons);
            if (timemark.capturedAt() == null || Duration.between(timemark.capturedAt(), serverTime).abs().getSeconds() > maxTimeDifferenceSeconds) {
                reasons.add("Thời gian trên ảnh Timemark không khớp giờ máy chủ");
            }
        }
        if (!reasons.isEmpty()) {
            throw new AttendanceException(HttpStatus.UNPROCESSABLE_ENTITY, "ATTENDANCE_VERIFICATION_FAILED",
                String.join("; ", reasons) + ". Vui lòng tải lại ảnh hoặc gửi giải trình.");
        }
    }

    private void validateCoordinates(Double latitude, Double longitude, String source, ArrayList<String> reasons) {
        if (latitude == null || longitude == null) {
            reasons.add(source + " không có tọa độ để đối chiếu");
            return;
        }
        var distance = distanceMeters(workplaceLatitude, workplaceLongitude, latitude, longitude);
        if (distance > radiusMeters) {
            reasons.add(source + " nằm ngoài bán kính điểm làm việc (" + Math.round(distance) + " m)");
        }
    }

    private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusMeters = 6_371_000;
        double deltaLatitude = Math.toRadians(lat2 - lat1);
        double deltaLongitude = Math.toRadians(lon2 - lon1);
        double a = Math.sin(deltaLatitude / 2) * Math.sin(deltaLatitude / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(deltaLongitude / 2) * Math.sin(deltaLongitude / 2);
        return 2 * earthRadiusMeters * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
