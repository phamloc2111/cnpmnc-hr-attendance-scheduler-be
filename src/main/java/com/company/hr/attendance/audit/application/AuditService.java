package com.company.hr.attendance.audit.application;

import com.company.hr.attendance.audit.AuditApi;
import com.company.hr.attendance.audit.api.AuditModels.AuditLogResponse;
import com.company.hr.attendance.audit.api.AuditModels.PageResponse;
import com.company.hr.attendance.audit.domain.AuditLog;
import com.company.hr.attendance.audit.infrastructure.AuditLogRepository;
import com.company.hr.attendance.identity.IdentityApi;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class AuditService implements AuditApi {

    private final AuditLogRepository repository;
    private final IdentityApi identity;

    public AuditService(AuditLogRepository repository, IdentityApi identity) {
        this.repository = repository;
        this.identity = identity;
    }

    @Override
    @Transactional
    public void record(
        UUID actorId,
        UUID targetEmployeeId,
        LocalDate targetDate,
        String category,
        String action,
        String summary,
        String oldValue,
        String newValue,
        Instant occurredAt
    ) {
        var entry = new AuditLog(
            UUID.randomUUID(),
            actorId,
            targetEmployeeId,
            targetDate != null ? targetDate : LocalDate.now(),
            category,
            action,
            summary != null && summary.length() > 500 ? summary.substring(0, 500) : (summary != null ? summary : action),
            oldValue,
            newValue,
            occurredAt != null ? occurredAt : Instant.now()
        );
        repository.save(entry);
    }

    public PageResponse<AuditLogResponse> list(
        UUID requesterId,
        UUID actorId,
        UUID employeeId,
        String category,
        LocalDate from,
        LocalDate to,
        int page,
        int size
    ) {
        var requester = identity.person(requesterId);
        if (requester == null || requester.locked() || (!requester.role().equals("ADMIN") && !requester.role().equals("MANAGER"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ quản trị viên hoặc quản lý mới được phép xem nhật ký thay đổi.");
        }

        if (from != null && to != null && to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoảng ngày lọc nhật ký không hợp lệ.");
        }

        Specification<AuditLog> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (actorId != null) {
                predicates.add(cb.equal(root.get("actorId"), actorId));
            }
            if (employeeId != null) {
                predicates.add(cb.equal(root.get("targetEmployeeId"), employeeId));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category.strip().toUpperCase()));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("targetDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("targetDate"), to));
            }
            if (requester.role().equals("MANAGER")) {
                var managedIds = new ArrayList<>(identity.managedEmployeeIds(requesterId));
                managedIds.add(requesterId);
                predicates.add(cb.or(
                    root.get("actorId").in(managedIds),
                    root.get("targetEmployeeId").in(managedIds)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<AuditLog> result = repository.findAll(
            spec,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt", "id"))
        );

        var personIds = new HashSet<UUID>();
        for (var log : result.getContent()) {
            if (log.getActorId() != null) personIds.add(log.getActorId());
            if (log.getTargetEmployeeId() != null) personIds.add(log.getTargetEmployeeId());
        }
        var names = personIds.isEmpty() ? Map.<UUID, String>of() : identity.names(personIds);

        return PageResponse.from(result.map(log -> AuditLogResponse.from(
            log,
            names.getOrDefault(log.getActorId(), "Hệ thống"),
            log.getTargetEmployeeId() != null ? names.getOrDefault(log.getTargetEmployeeId(), "Nhân viên") : null
        )));
    }
}
