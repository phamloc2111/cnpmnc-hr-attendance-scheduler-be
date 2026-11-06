package com.company.hr.attendance.identity.application;

import com.company.hr.attendance.identity.api.AccountModels.*;
import com.company.hr.attendance.organization.PositionsApi;
import com.company.hr.attendance.identity.domain.*;
import com.company.hr.attendance.identity.infrastructure.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final LoginSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final PositionsApi positions;

    public AccountService(AccountRepository accounts, LoginSessionRepository sessions, PasswordEncoder passwords, PositionsApi positions) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.passwords = passwords;
        this.positions = positions;
    }

    @Transactional(readOnly = true)
    public AccountPage list(UUID actorId, String query, Role role, Boolean locked, int page, int size) {
        var actor = actor(actorId);
        if (actor.getRole() == Role.MANAGER && role != null && role != Role.EMPLOYEE) {
            throw forbidden();
        }
        Specification<Account> filters = (root, ignored, cb) -> cb.conjunction();
        if (actor.getRole() == Role.MANAGER) {
            filters = filters.and((root, ignored, cb) -> cb.and(
                cb.equal(root.get("role"), Role.EMPLOYEE), cb.equal(root.get("managerId"), actorId)));
        }
        if (role != null) filters = filters.and((root, ignored, cb) -> cb.equal(root.get("role"), role));
        if (locked != null) filters = filters.and((root, ignored, cb) -> cb.equal(root.get("locked"), locked));
        if (query != null && !query.isBlank()) {
            var search = "%" + query.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            filters = filters.and((root, ignored, cb) -> cb.or(
                cb.like(cb.lower(root.get("displayName")), search, '\\'),
                cb.like(cb.lower(root.get("email")), search, '\\')));
        }
        return AccountPage.from(accounts.findAll(filters,
            PageRequest.of(page, size, Sort.by("displayName").ascending().and(Sort.by("id")))));
    }

    @Transactional(readOnly = true)
    public AccountResponse get(UUID actorId, UUID id) {
        return AccountResponse.from(target(actor(actorId), id));
    }

    @Transactional
    public AccountResponse create(UUID actorId, CreateRequest request) {
        accounts.lockActiveAdmins();
        var actor = actor(actorId);
        checkAssignment(actor, request.role(), request.managerId());
        var email = normalizeEmail(request.email());
        uniqueEmail(email, null);
        var selected = request.role() == Role.EMPLOYEE ? positions.select(request.positionId(), null) : null;
        var account = new Account(email, passwords.encode(request.password()), request.displayName().strip(), request.role());
        account.updateDetails(email, request.displayName().strip(), request.role(),
            selected == null ? null : selected.name(),
            request.role() == Role.EMPLOYEE ? normalizedSkills(request.skills()) : List.of(), assignedManager(actor, request.role(), request.managerId()));
        account.setPositionId(selected == null ? null : selected.id());
        return AccountResponse.from(accounts.saveAndFlush(account));
    }

    @Transactional
    public AccountResponse update(UUID actorId, UUID id, UpdateRequest request) {
        // Serialize administrative changes so two admins cannot concurrently remove each other's access.
        accounts.lockActiveAdmins();
        var actor = actor(actorId);
        var account = target(actor, id);
        checkVersion(account, request.version());
        if (id.equals(request.managerId())) {
            throw new AccountOperationException(HttpStatus.BAD_REQUEST, "INVALID_MANAGER",
                "Không thể gán tài khoản làm người quản lý của chính nó");
        }
        checkAssignment(actor, request.role(), request.managerId());
        if (id.equals(actorId) && request.role() != account.getRole()) {
            throw conflict("SELF_ROLE_CHANGE", "Bạn không thể tự thay đổi vai trò của mình");
        }
        if (account.getRole() == Role.MANAGER && request.role() != Role.MANAGER
            && accounts.existsByManagerId(id)) {
            throw conflict("MANAGER_HAS_EMPLOYEES", "Hãy chuyển nhân viên sang quản lý khác trước khi đổi vai trò");
        }
        var email = normalizeEmail(request.email());
        uniqueEmail(email, id);
        var selected = request.role() == Role.EMPLOYEE ? positions.select(request.positionId(), account.getPositionId()) : null;
        boolean identityChanged = account.getRole() != request.role() || !account.getEmail().equals(email);
        account.updateDetails(email, request.displayName().strip(), request.role(),
            selected == null ? null : selected.name(),
            request.role() == Role.EMPLOYEE ? normalizedSkills(request.skills()) : List.of(), assignedManager(actor, request.role(), request.managerId()));
        if (identityChanged) sessions.deleteByAccountId(id);
        account.setPositionId(selected == null ? null : selected.id());
        return AccountResponse.from(accounts.saveAndFlush(account));
    }

    @Transactional
    public AccountResponse setLocked(UUID actorId, UUID id, LockRequest request) {
        accounts.lockActiveAdmins();
        var actor = actor(actorId);
        var account = target(actor, id);
        checkVersion(account, request.version());
        if (id.equals(actorId) && request.locked()) {
            throw conflict("SELF_LOCK", "Bạn không thể tự khóa tài khoản của mình");
        }
        account.setLocked(request.locked());
        // Remove sessions on locking, so unlocking never resurrects an old JWT.
        if (request.locked()) sessions.deleteByAccountId(id);
        return AccountResponse.from(accounts.saveAndFlush(account));
    }

    private Account actor(UUID id) {
        var actor = accounts.findById(id).orElseThrow(AccountService::forbidden);
        if (actor.isLocked() || actor.getRole() == Role.EMPLOYEE) throw forbidden();
        return actor;
    }

    private Account target(Account actor, UUID id) {
        var target = accounts.findById(id).orElseThrow(AccountService::notFound);
        if (actor.getRole() == Role.MANAGER
            && (target.getRole() != Role.EMPLOYEE || !actor.getId().equals(target.getManagerId()))) {
            throw notFound();
        }
        return target;
    }

    private void checkAssignment(Account actor, Role role, UUID managerId) {
        if (actor.getRole() == Role.MANAGER) {
            if (role != Role.EMPLOYEE || managerId != null && !managerId.equals(actor.getId())) throw forbidden();
        } else if (role != Role.EMPLOYEE && managerId != null) {
            throw new AccountOperationException(HttpStatus.BAD_REQUEST, "INVALID_MANAGER",
                "Chỉ tài khoản nhân viên được gán người quản lý");
        } else if (managerId != null) {
            var manager = accounts.findById(managerId).orElseThrow(() -> new AccountOperationException(
                HttpStatus.BAD_REQUEST, "INVALID_MANAGER", "Người quản lý không tồn tại"));
            if (manager.getRole() != Role.MANAGER || manager.isLocked()) {
                throw new AccountOperationException(HttpStatus.BAD_REQUEST, "INVALID_MANAGER",
                    "Người quản lý phải là tài khoản quản lý đang hoạt động");
            }
        }
    }

    private UUID assignedManager(Account actor, Role role, UUID requested) {
        return role == Role.EMPLOYEE ? (actor.getRole() == Role.MANAGER ? actor.getId() : requested) : null;
    }

    private void uniqueEmail(String email, UUID ownId) {
        accounts.findByEmail(email).filter(existing -> !existing.getId().equals(ownId)).ifPresent(existing -> {
            throw conflict("EMAIL_EXISTS", "Email đã được sử dụng");
        });
    }

    private void checkVersion(Account account, long version) {
        if (account.getVersion() != version) {
            throw conflict("STALE_ACCOUNT", "Tài khoản vừa được cập nhật. Vui lòng tải lại dữ liệu trước khi lưu");
        }
    }

    private String normalizeEmail(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    private List<String> normalizedSkills(List<String> input) {
        var result = input.stream().map(String::strip).toList();
        if (result.stream().map(value -> value.toLowerCase(Locale.ROOT)).distinct().count() != result.size()) {
            throw new AccountOperationException(HttpStatus.BAD_REQUEST, "DUPLICATE_SKILL", "Kỹ năng không được trùng nhau");
        }
        return result;
    }

    private static AccountOperationException forbidden() {
        return new AccountOperationException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này");
    }
    private static AccountOperationException notFound() {
        return new AccountOperationException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Không tìm thấy tài khoản");
    }
    private static AccountOperationException conflict(String code, String message) {
        return new AccountOperationException(HttpStatus.CONFLICT, code, message);
    }
}
