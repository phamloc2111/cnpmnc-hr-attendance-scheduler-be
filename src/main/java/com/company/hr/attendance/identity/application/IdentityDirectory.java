package com.company.hr.attendance.identity.application;

import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.identity.domain.Account;
import com.company.hr.attendance.identity.domain.Role;
import com.company.hr.attendance.identity.infrastructure.AccountRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

@Service
@Transactional(readOnly = true)
public class IdentityDirectory implements IdentityApi {
    private final AccountRepository accounts;
    public IdentityDirectory(AccountRepository accounts) { this.accounts = accounts; }

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
