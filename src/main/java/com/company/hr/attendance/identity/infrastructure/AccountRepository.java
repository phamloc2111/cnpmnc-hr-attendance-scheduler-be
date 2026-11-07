package com.company.hr.attendance.identity.infrastructure;

import com.company.hr.attendance.identity.domain.Account;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.List;

public interface AccountRepository extends JpaRepository<Account, UUID>, JpaSpecificationExecutor<Account> {
    Optional<Account> findByEmail(String email);
    List<Account> findByManagerIdAndRole(UUID managerId, com.company.hr.attendance.identity.domain.Role role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> lockById(UUID id);
    boolean existsByManagerId(UUID managerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.role = com.company.hr.attendance.identity.domain.Role.ADMIN and a.locked = false order by a.id")
    List<Account> lockActiveAdmins();
}
