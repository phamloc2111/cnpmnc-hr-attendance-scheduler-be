package com.company.hr.attendance.shiftpass.infrastructure;

import com.company.hr.attendance.shiftpass.domain.ShiftPass;
import com.company.hr.attendance.shiftpass.domain.ShiftPassStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftPassRepository extends JpaRepository<ShiftPass, UUID> {
    boolean existsByScheduleIdAndStatus(UUID scheduleId, ShiftPassStatus status);

    List<ShiftPass> findByRequesterIdOrClaimerIdOrderByCreatedAtDesc(UUID requesterId, UUID claimerId);

    List<ShiftPass> findByStatusOrderByStartsAtAsc(ShiftPassStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ShiftPass p where p.id = :id")
    Optional<ShiftPass> lockById(@Param("id") UUID id);
}
