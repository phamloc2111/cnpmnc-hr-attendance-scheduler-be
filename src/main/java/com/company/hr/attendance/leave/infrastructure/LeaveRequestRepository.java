package com.company.hr.attendance.leave.infrastructure;

import com.company.hr.attendance.leave.domain.LeaveRequest;
import com.company.hr.attendance.leave.domain.LeaveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID>, JpaSpecificationExecutor<LeaveRequest> {

    long countByEmployeeIdAndStatusAndWorkDateBetween(UUID employeeId, LeaveStatus status, LocalDate from, LocalDate to);

    long countByEmployeeIdAndStatusInAndWorkDateBetween(UUID employeeId, Collection<LeaveStatus> statuses, LocalDate from, LocalDate to);

    boolean existsByScheduleIdAndStatusIn(UUID scheduleId, Collection<LeaveStatus> statuses);

    List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);
}
