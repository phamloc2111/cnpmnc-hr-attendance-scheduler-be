package com.company.hr.attendance.workschedule.infrastructure;

import com.company.hr.attendance.workschedule.domain.ScheduleDraftWeek;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface ScheduleDraftWeekRepository extends JpaRepository<ScheduleDraftWeek, UUID> {
    boolean existsByManagerIdAndWeekStart(UUID managerId, LocalDate weekStart);
    @Modifying
    @Query(value = "INSERT INTO schedule_draft_weeks(id, manager_id, week_start) "
        + "VALUES (:id, :managerId, :weekStart) ON CONFLICT (manager_id, week_start) DO NOTHING", nativeQuery = true)
    int createIfAbsent(@Param("id") UUID id, @Param("managerId") UUID managerId,
        @Param("weekStart") LocalDate weekStart);
}
