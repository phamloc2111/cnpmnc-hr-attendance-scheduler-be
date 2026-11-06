package com.company.hr.attendance.organization.application;

import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.organization.PositionsApi;
import com.company.hr.attendance.organization.api.PositionModels.*;
import com.company.hr.attendance.organization.domain.*;
import com.company.hr.attendance.organization.infrastructure.PositionRepository;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.http.HttpStatus.*;

@Service
@Transactional(readOnly = true)
public class PositionService implements PositionsApi {
    private final IdentityApi identity;
    private final PositionRepository positions;
    public PositionService(IdentityApi identity, PositionRepository positions) { this.identity=identity; this.positions=positions; }
    public PositionPage list(UUID actorId, String query, Boolean active, int page, int size) {
        actor(actorId, false);
        Specification<Position> filter = (root, ignored, cb) -> cb.conjunction();
        if (active != null) filter = filter.and((root, ignored, cb) -> cb.equal(root.get("active"),active));
        if (query != null && !query.isBlank()) {
            var search = "%" + PositionName.key(query).replace("\\","\\\\").replace("%","\\%").replace("_","\\_") + "%";
            filter = filter.and((root, ignored, cb) -> cb.like(root.get("normalizedName"),search,'\\'));
        }
        return PositionPage.from(positions.findAll(filter, PageRequest.of(page,size,Sort.by("name","id"))));
    }
    public PositionResponse get(UUID actorId, UUID id) {
        actor(actorId, false); return PositionResponse.from(positions.findById(id).orElseThrow(PositionService::notFound));
    }
    @Transactional
    public PositionResponse create(UUID actorId, CreatePosition request) {
        actor(actorId, true);
        var name = PositionName.display(request.name());
        if (name.isBlank()) throw new PositionException(BAD_REQUEST,"INVALID_POSITION","Tên vị trí không được để trống.");
        if (positions.existsByNormalizedName(PositionName.key(name))) {
            throw new PositionException(CONFLICT,"POSITION_EXISTS","Vị trí đã tồn tại, kể cả vị trí ngừng sử dụng.");
        }
        return PositionResponse.from(positions.saveAndFlush(new Position(name,actorId)));
    }
    @Transactional
    public PositionResponse status(UUID actorId, UUID id, SetStatus request) {
        actor(actorId, true);
        var position=positions.lockForChange(id).orElseThrow(PositionService::notFound);
        if (position.getVersion()!=request.version()) throw new PositionException(CONFLICT,"STALE_POSITION","Vị trí vừa thay đổi. Vui lòng tải lại.");
        position.setActive(request.active());
        return PositionResponse.from(positions.saveAndFlush(position));
    }
    @Transactional
    public Selection select(UUID id, UUID currentId) {
        if (id == null) return null;
        var position=positions.lockForSelection(id).orElseThrow(PositionService::notFound);
        if (!position.isActive() && !id.equals(currentId)) {
            throw new PositionException(CONFLICT,"POSITION_INACTIVE","Vị trí đã ngừng sử dụng. Vui lòng chọn vị trí khác.");
        }
        return new Selection(position.getId(),position.getName());
    }
    private void actor(UUID actorId, boolean write) {
        var actor=identity.person(actorId);
        if (actor.locked() || (write ? !actor.role().equals("MANAGER") : !Set.of("ADMIN","MANAGER").contains(actor.role()))) {
            throw new PositionException(FORBIDDEN,"FORBIDDEN","Chỉ quản lý được tạo và thay đổi danh mục vị trí.");
        }
    }
    private static PositionException notFound() {
        return new PositionException(NOT_FOUND,"POSITION_NOT_FOUND","Không tìm thấy vị trí trong danh mục.");
    }
}
