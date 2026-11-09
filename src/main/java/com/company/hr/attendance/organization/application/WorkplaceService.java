package com.company.hr.attendance.organization.application;

import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.organization.WorkplaceApi;
import com.company.hr.attendance.organization.api.WorkplaceModels.*;
import com.company.hr.attendance.organization.infrastructure.WorkplaceRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkplaceService implements WorkplaceApi {
    private final WorkplaceRepository repository;
    private final IdentityApi identity;
    private final Optional<Configuration> environment;
    public WorkplaceService(WorkplaceRepository repository, IdentityApi identity,
        @Value("${app.attendance.verification.workplace-latitude:}") Double latitude,
        @Value("${app.attendance.verification.workplace-longitude:}") Double longitude,
        @Value("${app.attendance.verification.radius-meters:100}") int radius,
        @Value("${app.attendance.verification.gps-tolerance-meters:30}") int tolerance) {
        this.repository = repository; this.identity = identity;
        environment = valid(latitude, longitude, radius, tolerance)
            ? Optional.of(new Configuration(latitude, longitude, radius, tolerance, 0, null, null)) : Optional.empty();
    }
    @Override public Optional<Configuration> current() { return repository.find().or(() -> environment); }
    public WorkplaceResponse get(UUID actorId) { authorize(actorId); return response(); }
    @Transactional
    public WorkplaceResponse save(UUID actorId, SaveWorkplace request) {
        authorize(actorId);
        if (request == null || request.radiusMeters() == null || request.gpsToleranceMeters() == null ||
            request.version() == null || request.version() < 0 ||
            !valid(request.latitude(), request.longitude(), request.radiusMeters(), request.gpsToleranceMeters())) {
            throw new WorkplaceException(HttpStatus.BAD_REQUEST, "INVALID_WORKPLACE",
                "Cần tọa độ hợp lệ, bán kính 10–5000m và dung sai GPS 20–50m.");
        }
        if (!repository.save(request.latitude(), request.longitude(), request.radiusMeters(), request.gpsToleranceMeters(),
                request.version(), actorId, Instant.now())) {
            throw new WorkplaceException(HttpStatus.CONFLICT, "STALE_WORKPLACE",
                "Cấu hình vừa được người khác thay đổi. Hãy tải lại trước khi lưu.");
        }
        return response();
    }
    private WorkplaceResponse response() {
        return current().map(value -> new WorkplaceResponse(true, value.latitude(), value.longitude(), value.radiusMeters(),
            value.gpsToleranceMeters(), value.version(), value.updatedAt(), value.updatedBy()))
            .orElseGet(() -> new WorkplaceResponse(false, null, null, 100, 30, 0, null, null));
    }
    private void authorize(UUID actorId) {
        var actor = identity.person(actorId);
        if (actor.locked() || !Set.of("MANAGER", "ADMIN").contains(actor.role())) {
            throw new WorkplaceException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ quản lý và quản trị được cấu hình điểm làm việc.");
        }
    }
    private static boolean valid(Double latitude, Double longitude, int radius, int tolerance) {
        return latitude != null && longitude != null && Double.isFinite(latitude) && Double.isFinite(longitude)
            && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180
            && radius >= 10 && radius <= 5000 && tolerance >= 20 && tolerance <= 50;
    }
}
