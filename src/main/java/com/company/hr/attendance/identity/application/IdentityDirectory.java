package com.company.hr.attendance.identity.application;

import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.identity.domain.Account;
import com.company.hr.attendance.identity.domain.Role;
import com.company.hr.attendance.identity.infrastructure.AccountRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

@Service
@Transactional(readOnly = true)
public class IdentityDirectory implements IdentityApi {
    private final AccountRepository accounts;
    private final com.company.hr.attendance.identity.infrastructure.LoginSessionRepository sessions;
    public IdentityDirectory(AccountRepository accounts, com.company.hr.attendance.identity.infrastructure.LoginSessionRepository sessions) { this.accounts = accounts; this.sessions = sessions; }

    public Map<UUID, ScoreProfile> scoreProfiles(Collection<UUID> ids) {
        var profiles = new HashMap<UUID, ScoreProfile>();
        accounts.findAllById(ids).forEach(account -> profiles.put(account.getId(),
            new ScoreProfile(account.getId(), account.getDisplayName(), account.isLocked(), account.getManagerId(),
                account.getCreatedAt(), account.getPositionId(), List.copyOf(account.getSkills()))));
        return profiles;
    }
    public Person person(UUID id) {
        return view(accounts.findById(id).orElseThrow(() -> new AccessDeniedException("Account unavailable")));
    }
    @Transactional
    public Person lockPerson(UUID id) {
        return view(accounts.lockById(id).orElseThrow(() -> new AccessDeniedException("Account unavailable")));
    }
    public List<UUID> managedEmployeeIds(UUID managerId) {
        return accounts.findByManagerIdAndRole(managerId, Role.EMPLOYEE).stream().map(Account::getId).toList();
    }
    @Override
    public List<UUID> activeEmployeeIdsByPosition(UUID positionId) {
        if (positionId == null) return List.of();
        return accounts.findByPositionIdAndRoleAndLockedFalse(positionId, Role.EMPLOYEE).stream().map(Account::getId).toList();
    }
    @Override
    public List<UUID> activeEmployeeIdsByManager(UUID managerId) {
        if (managerId == null) return List.of();
        return accounts.findByManagerIdAndRoleAndLockedFalse(managerId, Role.EMPLOYEE).stream().map(Account::getId).toList();
    }
    public Page<Person> managedEmployees(UUID managerId, String query, int page, int size) {
        Specification<Account> filters = (root, ignored, cb) -> cb.and(
            cb.equal(root.get("managerId"), managerId), cb.equal(root.get("role"), Role.EMPLOYEE));
        if (query != null && !query.isBlank()) {
            String search = "%" + query.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            filters = filters.and((root, ignored, cb) -> cb.or(cb.like(cb.lower(root.get("displayName")), search, '\\'), cb.like(cb.lower(root.get("email")), search, '\\')));
        }
        return accounts.findAll(filters, PageRequest.of(page, size,
            Sort.by("displayName", "id"))).map(this::view);
    }
    public Page<Person> employeePage(UUID actorId, String query, int page, int size) {
        var actor = person(actorId);
        if (actor.locked() || !Set.of("MANAGER", "ADMIN").contains(actor.role()))
            throw new AccessDeniedException("Management access required");
        if (actor.role().equals("MANAGER")) return managedEmployees(actorId, query, page, size);
        Specification<Account> filters = (root, ignored, cb) -> cb.equal(root.get("role"), Role.EMPLOYEE);
        if (query != null && !query.isBlank()) {
            String search = "%" + query.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            filters = filters.and((root, ignored, cb) -> cb.or(cb.like(cb.lower(root.get("displayName")), search, '\\'), cb.like(cb.lower(root.get("email")), search, '\\')));
        }
        return accounts.findAll(filters, PageRequest.of(page, size, Sort.by("displayName", "id"))).map(this::view);
    }
    public boolean canReceiveRegistrationReminders(UUID employeeId, UUID sessionId) {
        // Push consent outlives JWT expiry; explicit logout/revocation removes the session row.
        var account=accounts.findById(employeeId);var session=sessions.findById(sessionId);
        return account.isPresent() && !account.get().isLocked() && account.get().getRole()==Role.EMPLOYEE
            && session.isPresent() && session.get().getAccountId().equals(employeeId);
    }
    public Map<UUID, String> names(Collection<UUID> ids) {
        var result = new HashMap<UUID, String>();
        accounts.findAllById(ids).forEach(account -> result.put(account.getId(), account.getDisplayName()));
        return result;
    }
    private Person view(Account account) {
        return new Person(account.getId(), account.getDisplayName(), account.getRole().name(),
            account.isLocked(), account.getManagerId());
    }
}
