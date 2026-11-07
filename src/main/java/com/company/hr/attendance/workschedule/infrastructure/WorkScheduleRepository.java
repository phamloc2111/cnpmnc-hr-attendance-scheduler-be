package com.company.hr.attendance.workschedule.infrastructure;
import com.company.hr.attendance.workschedule.domain.WorkSchedule;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.*;
public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, UUID>, JpaSpecificationExecutor<WorkSchedule> {
    boolean existsByEmployeeIdAndStartsAtLessThanAndEndsAtGreaterThan(UUID employeeId, Instant end, Instant start);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select schedule from WorkSchedule schedule where schedule.id = :id")
    java.util.Optional<WorkSchedule> lockById(@Param("id") UUID id);
}
