package com.company.hr.attendance.attendance.application;

import com.company.hr.attendance.attendance.api.AttendanceModels.TimemarkEvidence;
import com.company.hr.attendance.organization.WorkplaceApi;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AttendanceVerificationService {
    private final WorkplaceApi workplaces;
    private final long maxTimeDifferenceSeconds;
    public AttendanceVerificationService(WorkplaceApi workplaces,
        @Value("${app.attendance.verification.max-time-difference-seconds:300}") long maxTimeDifferenceSeconds) {
        this.workplaces = workplaces; this.maxTimeDifferenceSeconds = maxTimeDifferenceSeconds;
    }
    public void verify(TimemarkEvidence timemark, Instant serverTime) {
        if (timemark == null) {
            throw new AttendanceException(HttpStatus.UNPROCESSABLE_ENTITY, "ATTENDANCE_VERIFICATION_FAILED",
                "Không có thông tin xác minh từ ảnh Timemark. Vui lòng chụp và gửi lại ảnh tại điểm làm việc.");
        }
        verify(timemark.latitude(), timemark.longitude(), timemark.capturedAt(), serverTime);
    }

    public void verify(Double latitude, Double longitude, Instant capturedAt, Instant serverTime) {
        var workplace = workplaces.current().orElseThrow(() -> new AttendanceException(HttpStatus.SERVICE_UNAVAILABLE,
            "WORKPLACE_NOT_CONFIGURED", "Chưa cấu hình điểm làm việc. Vui lòng liên hệ quản lý vào mục Điểm làm việc để thiết lập tọa độ và bán kính."));
        var reasons = new ArrayList<String>();
        validateCoordinates(latitude, longitude, "Vị trí trên ảnh Timemark", workplace, reasons);
        if (capturedAt == null || maxTimeDifferenceSeconds < 0 ||
            Duration.between(capturedAt, serverTime).abs().compareTo(Duration.ofSeconds(maxTimeDifferenceSeconds)) > 0) {
            reasons.add("Thời gian trên ảnh Timemark không khớp giờ máy chủ");
        }
        if (!reasons.isEmpty()) throw new AttendanceException(HttpStatus.UNPROCESSABLE_ENTITY, "ATTENDANCE_VERIFICATION_FAILED",
            String.join("; ", reasons) + ". Vui lòng chụp và gửi lại ảnh tại điểm làm việc.");
    }

    public void verify(Double deviceLatitude, Double deviceLongitude, Double accuracyMeters,
                       TimemarkEvidence timemark, Instant serverTime) {
        var workplace = workplaces.current().orElseThrow(() -> new AttendanceException(HttpStatus.SERVICE_UNAVAILABLE,
            "WORKPLACE_NOT_CONFIGURED", "Chưa cấu hình điểm làm việc. Vui lòng liên hệ quản lý vào mục Điểm làm việc để thiết lập tọa độ và bán kính."));
        if (accuracyMeters == null || !Double.isFinite(accuracyMeters) || accuracyMeters < 0 ||
            accuracyMeters > workplace.gpsToleranceMeters()) {
            throw new AttendanceException(HttpStatus.UNPROCESSABLE_ENTITY, "GPS_ACCURACY_TOO_LOW",
                "GPS chưa đủ chính xác. Mức sai số cho phép là ±" + workplace.gpsToleranceMeters() +
                "m. Hãy bật vị trí chính xác, đến nơi thoáng và thử lại.");
        }
        var reasons = new ArrayList<String>();
        validateCoordinates(deviceLatitude, deviceLongitude, "Định vị điện thoại", workplace, reasons);
        if (timemark == null) reasons.add("Không có thời gian đọc từ ảnh Timemark");
        else {
            validateCoordinates(timemark.latitude(), timemark.longitude(), "Vị trí trên ảnh Timemark", workplace, reasons);
            if (timemark.capturedAt() == null || maxTimeDifferenceSeconds < 0 ||
                Duration.between(timemark.capturedAt(), serverTime).abs().compareTo(Duration.ofSeconds(maxTimeDifferenceSeconds)) > 0) {
                reasons.add("Thời gian trên ảnh Timemark không khớp giờ máy chủ");
            }
        }
        if (!reasons.isEmpty()) throw new AttendanceException(HttpStatus.UNPROCESSABLE_ENTITY, "ATTENDANCE_VERIFICATION_FAILED",
            String.join("; ", reasons) + ". Vui lòng chụp và gửi lại ảnh tại điểm làm việc.");
    }
    private void validateCoordinates(Double latitude, Double longitude, String source, WorkplaceApi.Configuration workplace,
                                     ArrayList<String> reasons) {
        if (latitude == null || longitude == null || !Double.isFinite(latitude) || !Double.isFinite(longitude) ||
            latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            reasons.add(source + " có tọa độ không hợp lệ"); return;
        }
        var distance = distanceMeters(workplace.latitude(), workplace.longitude(), latitude, longitude);
        var allowed = workplace.radiusMeters() + workplace.gpsToleranceMeters();
        if (distance > allowed + .000001) reasons.add(source + " nằm ngoài vùng điểm danh (cách " + Math.round(distance) +
            "m, bán kính " + workplace.radiusMeters() + "m + dung sai " + workplace.gpsToleranceMeters() + "m)");
    }
    private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double deltaLatitude = Math.toRadians(lat2 - lat1), deltaLongitude = Math.toRadians(lon2 - lon1);
        double a = Math.sin(deltaLatitude / 2) * Math.sin(deltaLatitude / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(deltaLongitude / 2) * Math.sin(deltaLongitude / 2);
        a = Math.max(0, Math.min(1, a));
        return 2 * 6_371_000 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
