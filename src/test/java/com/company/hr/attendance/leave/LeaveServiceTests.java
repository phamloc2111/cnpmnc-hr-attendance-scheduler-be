package com.company.hr.attendance.leave;

import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.leave.api.LeaveModels.*;
import com.company.hr.attendance.leave.application.LeaveService;
import com.company.hr.attendance.leave.domain.LeaveRequest;
import com.company.hr.attendance.leave.domain.LeaveStatus;
import com.company.hr.attendance.leave.infrastructure.LeaveRequestRepository;
import com.company.hr.attendance.workschedule.domain.WorkSchedule;
import com.company.hr.attendance.workschedule.infrastructure.ShiftTemplateRepository;
import com.company.hr.attendance.workschedule.infrastructure.WorkScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LeaveServiceTests {
    private LeaveRequestRepository leaveRepository;
    private WorkScheduleRepository scheduleRepository;
    private ShiftTemplateRepository shiftRepository;
    private IdentityApi identity;
    private JdbcTemplate jdbc;
    private LeaveService service;

    @BeforeEach
    void setUp() {
        leaveRepository = mock(LeaveRequestRepository.class);
        scheduleRepository = mock(WorkScheduleRepository.class);
        shiftRepository = mock(ShiftTemplateRepository.class);
        identity = mock(IdentityApi.class);
        jdbc = mock(JdbcTemplate.class);
        service = new LeaveService(leaveRepository, scheduleRepository, shiftRepository, identity, jdbc);
    }

    @Test
    void getQuota_returnsRemainingCorrectly() {
        UUID empId = UUID.randomUUID();
        when(identity.person(empId)).thenReturn(new IdentityApi.Person(empId, "Test Emp", "EMPLOYEE", false, null));
        when(leaveRepository.countByEmployeeIdAndStatusAndWorkDateBetween(eq(empId), eq(LeaveStatus.APPROVED), any(), any())).thenReturn(1L);
        when(leaveRepository.countByEmployeeIdAndStatusAndWorkDateBetween(eq(empId), eq(LeaveStatus.PENDING), any(), any())).thenReturn(0L);

        var quota = service.getQuota(empId, YearMonth.of(2026, 11));

        assertThat(quota.maxAllowed()).isEqualTo(2);
        assertThat(quota.approved()).isEqualTo(1);
        assertThat(quota.remaining()).isEqualTo(1);
        assertThat(quota.canSubmit()).isTrue();
    }

    @Test
    void submit_throwsWhenQuotaExceeded() {
        UUID empId = UUID.randomUUID();
        when(identity.person(empId)).thenReturn(new IdentityApi.Person(empId, "Test Emp", "EMPLOYEE", false, null));
        when(identity.lockPerson(empId)).thenReturn(new IdentityApi.Person(empId, "Test Emp", "EMPLOYEE", false, null));
        when(leaveRepository.countByEmployeeIdAndStatusAndWorkDateBetween(eq(empId), eq(LeaveStatus.APPROVED), any(), any())).thenReturn(2L);
        when(leaveRepository.countByEmployeeIdAndStatusAndWorkDateBetween(eq(empId), eq(LeaveStatus.PENDING), any(), any())).thenReturn(0L);

        var req = new CreateLeaveRequest(null, LocalDate.of(2026, 11, 20), null, "Bị ốm");

        assertThatThrownBy(() -> service.submit(empId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Bạn đã hết số lần xin nghỉ phép trong tháng");
    }

    @Test
    void review_approvesAndUpdatesSchedule() {
        UUID mgrId = UUID.randomUUID();
        UUID empId = UUID.randomUUID();
        UUID leaveId = UUID.randomUUID();
        UUID schedId = UUID.randomUUID();

        when(identity.person(mgrId)).thenReturn(new IdentityApi.Person(mgrId, "Manager", "MANAGER", false, null));
        when(identity.person(empId)).thenReturn(new IdentityApi.Person(empId, "Employee", "EMPLOYEE", false, null));

        var leave = new LeaveRequest(leaveId, empId, schedId, LocalDate.of(2026, 11, 20), null, "Ca sáng", "Việc gia đình", java.time.Instant.now());
        when(leaveRepository.findById(leaveId)).thenReturn(Optional.of(leave));

        var result = service.review(mgrId, leaveId, new ReviewLeaveRequest("APPROVE", "Đồng ý cho nghỉ"));

        assertThat(result.status()).isEqualTo(LeaveStatus.APPROVED);
        verify(jdbc).update(contains("UPDATE work_schedules SET understaffed"), eq(schedId));
    }
}
