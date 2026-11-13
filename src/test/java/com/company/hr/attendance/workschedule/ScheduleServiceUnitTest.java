package com.company.hr.attendance.workschedule;

import com.company.hr.attendance.attendance.AttendanceApi;
import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.organization.PositionsApi;
import com.company.hr.attendance.workschedule.application.ScheduleException;
import com.company.hr.attendance.workschedule.api.ScheduleModels.*;
import com.company.hr.attendance.workschedule.application.ScheduleService;
import com.company.hr.attendance.workschedule.domain.ShiftTemplate;
import com.company.hr.attendance.workschedule.domain.WorkSchedule;
import com.company.hr.attendance.workschedule.infrastructure.ShiftTemplateRepository;
import com.company.hr.attendance.workschedule.infrastructure.WorkScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ScheduleServiceUnitTest {
    private IdentityApi identity;
    private ShiftTemplateRepository shifts;
    private WorkScheduleRepository schedules;
    private PositionsApi positions;
    private AttendanceApi attendance;
    private ScheduleService service;

    private final UUID managerId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID positionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        identity = mock(IdentityApi.class);
        shifts = mock(ShiftTemplateRepository.class);
        schedules = mock(WorkScheduleRepository.class);
        positions = mock(PositionsApi.class);
        attendance = mock(AttendanceApi.class);

        when(identity.person(managerId)).thenReturn(new IdentityApi.Person(managerId, "Manager", "MANAGER", false, null));
        when(identity.person(employeeId)).thenReturn(new IdentityApi.Person(employeeId, "Employee", "EMPLOYEE", false, managerId));
        when(identity.lockPerson(employeeId)).thenReturn(new IdentityApi.Person(employeeId, "Employee", "EMPLOYEE", false, managerId));

        service = new ScheduleService(identity, shifts, schedules, "Asia/Ho_Chi_Minh", positions, attendance);
    }

    @Test
    void reassign_checksHardOverlapConstraint() {
        UUID scheduleId = UUID.randomUUID();
        UUID shiftId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 11, 20);

        ShiftTemplate oldShift = new ShiftTemplate("Ca 1", LocalTime.of(8, 0), LocalTime.of(12, 0), 0, managerId, 1, "Thu ngân", positionId, false);
        WorkSchedule existingSchedule = new WorkSchedule(employeeId, oldShift, date, ZoneId.of("Asia/Ho_Chi_Minh"), managerId);
        when(schedules.lockById(scheduleId)).thenReturn(Optional.of(existingSchedule));
        when(schedules.findById(scheduleId)).thenReturn(Optional.of(existingSchedule));

        ShiftTemplate targetShift = new ShiftTemplate("Ca 2", LocalTime.of(13, 0), LocalTime.of(17, 0), 0, managerId, 1, "Thu ngân", positionId, false);
        when(shifts.findById(shiftId)).thenReturn(Optional.of(targetShift));

        when(schedules.existsByEmployeeIdAndIdNotAndStartsAtLessThanAndEndsAtGreaterThan(
            eq(employeeId), eq(scheduleId), any(Instant.class), any(Instant.class)
        )).thenReturn(true);

        assertThatThrownBy(() -> service.reassign(managerId, scheduleId, new ReassignShift(employeeId, shiftId, date)))
            .isInstanceOf(ScheduleException.class)
            .hasMessageContaining("Nhân viên đã có ca trùng giờ, vi phạm ràng buộc cứng.");
    }

    @Test
    void publish_blocksOvertimeWithoutConfirmation() {
        LocalDate from = LocalDate.of(2026, 11, 16);
        LocalDate to = LocalDate.of(2026, 11, 22);

        ShiftTemplate longShift = new ShiftTemplate("Ca dài", LocalTime.of(8, 0), LocalTime.of(20, 0), 0, managerId, 1, "Thu ngân", positionId, false);

        List<WorkSchedule> scheduleList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            scheduleList.add(new WorkSchedule(employeeId, longShift, from.plusDays(i), ZoneId.of("Asia/Ho_Chi_Minh"), managerId));
        }

        when(schedules.findAll(any(Specification.class))).thenReturn(scheduleList);

        assertThatThrownBy(() -> service.publish(managerId, new PublishScheduleRequest(from, to, null, false)))
            .isInstanceOf(ScheduleException.class)
            .hasMessageContaining("vượt quá giới hạn giờ làm trong tuần (48 giờ)");

        var res = service.publish(managerId, new PublishScheduleRequest(from, to, null, true));
        assertThat(res.publishedCount()).isEqualTo(5);
        assertThat(scheduleList.get(0).isPublished()).isTrue();
    }
}
