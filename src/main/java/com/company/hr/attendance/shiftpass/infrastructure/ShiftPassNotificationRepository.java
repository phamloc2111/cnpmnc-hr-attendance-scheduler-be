package com.company.hr.attendance.shiftpass.infrastructure;

import com.company.hr.attendance.shiftpass.domain.ShiftPassNotification;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftPassNotificationRepository extends JpaRepository<ShiftPassNotification, UUID> {
    List<ShiftPassNotification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

    @Modifying
    @Query("update ShiftPassNotification n set n.status = 'CLOSED', n.closedAt = :now where n.shiftPassId = :passId and n.status = 'OPEN'")
    int closeOpenByPassId(@Param("passId") UUID passId, @Param("now") Instant now);
}
