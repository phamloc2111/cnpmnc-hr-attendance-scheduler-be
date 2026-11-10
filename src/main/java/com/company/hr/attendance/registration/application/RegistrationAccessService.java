package com.company.hr.attendance.registration.application;

import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.registration.api.RegistrationModels.*;
import com.company.hr.attendance.registration.domain.RegistrationWindow;
import com.company.hr.attendance.registration.infrastructure.RegistrationRepository;
import com.company.hr.attendance.registration.infrastructure.RegistrationRepository.Grant;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.http.HttpStatus.*;

@Service
@Transactional(readOnly = true)
public class RegistrationAccessService {
    private final IdentityApi identity;
    private final RegistrationRepository registrations;
    private final Clock clock;
    public RegistrationAccessService(IdentityApi identity, RegistrationRepository registrations, @Qualifier("registrationClock") Clock clock) {
        this.identity = identity; this.registrations = registrations; this.clock = clock;
    }
    public ManagedWeek list(UUID actorId, LocalDate requestedWeek, String query, int page, int size) {
        manager(actorId);
        var now = clock.instant();
        // After Sunday's close, managers usually need to reopen the week that has just started.
        var week = requestedWeek == null ? now.atZone(RegistrationWindow.ZONE).toLocalDate()
            .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : monday(requestedWeek);
        var window = RegistrationWindow.forWeek(week);
        var people = identity.managedEmployees(actorId, query, page, size);
        var ids = people.getContent().stream().map(IdentityApi.Person::id).toList();
        var grants = registrations.grants(week, ids); var counts = registrations.counts(week, ids);
        var items = people.getContent().stream().map(person -> view(person, week, now,
            ownedGrant(person, grants.get(person.id())), counts.getOrDefault(person.id(), 0))).toList();
        return new ManagedWeek(week, week.plusDays(6), now, window.opensAt(), window.closesAt(),
            items, page, size, people.getTotalElements(), people.getTotalPages());
    }
    @Transactional
    public ManagedEmployee change(UUID actorId, UUID employeeId, AccessChange request) {
        manager(actorId);
        // The same employee lock serializes grant/close operations against employee saves.
        var person = identity.lockPerson(employeeId);
        if (!person.role().equals("EMPLOYEE") || !actorId.equals(person.managerId())) throw new RegistrationException(FORBIDDEN,
            "REGISTRATION_EMPLOYEE_FORBIDDEN", "Bạn chỉ được quản lý đăng ký của nhân viên mình phụ trách.");
        var week = monday(request.weekStart()); var now = clock.instant();
        if (now.isBefore(RegistrationWindow.forWeek(week).closesAt())) throw new RegistrationException(CONFLICT,
            "REGISTRATION_NOT_CLOSED", "Chỉ mở lại đăng ký sau khi cổng đăng ký của tuần đã đóng.");
        var grant = ownedGrant(person, registrations.grant(employeeId, week).orElse(null));
        if (!accessRevision(grant).equals(request.accessRevision())) throw new RegistrationException(CONFLICT,
            "STALE_REGISTRATION_ACCESS", "Quyền đăng ký vừa thay đổi. Hãy tải lại trước khi thao tác.");
        if (request.reopened() && person.locked()) throw new RegistrationException(CONFLICT,
            "REGISTRATION_EMPLOYEE_LOCKED", "Không thể mở đăng ký cho tài khoản đang bị khóa.");
        if (request.reopened()) {
            if (grant == null) registrations.reopen(employeeId, week, actorId, now);
        } else registrations.close(employeeId, week);
        return view(person, week, now, ownedGrant(person, registrations.grant(employeeId, week).orElse(null)),
            registrations.ids(employeeId, week).size());
    }
    public boolean reopened(IdentityApi.Person person, LocalDate week) {
        return ownedGrant(person, registrations.grant(person.id(), week).orElse(null)) != null;
    }
    private Grant ownedGrant(IdentityApi.Person person, Grant grant) {
        return grant != null && grant.reopenedBy().equals(person.managerId()) ? grant : null;
    }
    private ManagedEmployee view(IdentityApi.Person person, LocalDate week, Instant now, Grant grant, int count) {
        var window = RegistrationWindow.forWeek(week);
        boolean closed = !now.isBefore(window.closesAt());
        var state = person.locked() ? "ACCOUNT_LOCKED" : closed && grant != null ? "REOPENED" : window.editable(now) ? "OPEN" : now.isBefore(window.opensAt()) ? "NOT_OPEN" : "CLOSED";
        return new ManagedEmployee(person.id(), person.name(), person.locked(), count, state,
            grant == null ? null : grant.reopenedAt(), accessRevision(grant), closed && !person.locked() && grant == null, closed && grant != null);
    }
    private String accessRevision(Grant grant) { return grant == null ? "closed" : grant.id().toString(); }
    private void manager(UUID actorId) {
        var actor = identity.person(actorId);
        if (actor.locked() || !actor.role().equals("MANAGER")) throw new RegistrationException(FORBIDDEN,
            "FORBIDDEN", "Chỉ quản lý đang hoạt động được mở lại đăng ký ca.");
    }
    private LocalDate monday(LocalDate value) {
        if (value == null || value.getDayOfWeek() != DayOfWeek.MONDAY) throw new RegistrationException(BAD_REQUEST,
            "INVALID_REGISTRATION_WEEK", "Tuần đăng ký phải bắt đầu vào thứ Hai.");
        return value;
    }
}
