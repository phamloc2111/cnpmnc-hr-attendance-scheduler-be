package com.company.hr.attendance.organization.application;

import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.organization.api.PlaceModels.*;
import com.company.hr.attendance.organization.infrastructure.GoongPlacesClient;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class WorkplacePlacesService {
    private final IdentityApi identity;
    private final GoongPlacesClient client;
    public WorkplacePlacesService(IdentityApi identity, GoongPlacesClient client) { this.identity = identity; this.client = client; }
    public List<Suggestion> search(UUID actorId, String query) {
        authorize(actorId);
        if (query == null || query.strip().length() < 3 || query.strip().length() > 200) throw invalid();
        return client.search(query.strip());
    }
    public Place detail(UUID actorId, String placeId) {
        authorize(actorId);
        if (placeId == null || placeId.isBlank() || placeId.length() > 2048 || placeId.chars().anyMatch(Character::isISOControl)) throw invalid();
        return client.detail(placeId);
    }
    private void authorize(UUID actorId) {
        var actor = identity.person(actorId);
        if (actor.locked() || !Set.of("MANAGER", "ADMIN").contains(actor.role())) {
            throw new WorkplaceException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ quản lý và quản trị được tìm địa chỉ điểm làm việc.");
        }
    }
    private WorkplaceException invalid() {
        return new WorkplaceException(HttpStatus.BAD_REQUEST, "INVALID_PLACE_QUERY", "Nhập địa chỉ từ 3 đến 200 ký tự hoặc chọn lại địa chỉ hợp lệ.");
    }
}
