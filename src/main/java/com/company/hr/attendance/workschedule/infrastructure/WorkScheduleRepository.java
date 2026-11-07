package com.company.hr.attendance.workschedule.infrastructure;
import com.company.hr.attendance.workschedule.domain.WorkSchedule;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, UUID>, JpaSpecificationExecutor<WorkSchedule> {
    boolean existsByEmployeeIdAndStartsAtLessThanAndEndsAtGreaterThan(UUID employeeId, Instant end, Instant start);
}
