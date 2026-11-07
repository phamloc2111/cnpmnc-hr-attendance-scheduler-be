package com.company.hr.attendance.attendance;

import com.company.hr.attendance.attendance.api.AttendanceModels.EventType;
import com.company.hr.attendance.attendance.application.AttendanceException;
import com.company.hr.attendance.attendance.application.AttendanceService;
import com.company.hr.attendance.attendance.infrastructure.AttendanceEventRepository;
import com.company.hr.attendance.attendance.infrastructure.AttendanceRecordRepository;
import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.workschedule.WorkScheduleApi;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttendanceServiceTest {
    private final AttendanceEventRepository events = mock(AttendanceEventRepository.class);
    private final AttendanceRecordRepository records = mock(AttendanceRecordRepository.class);
    private final WorkScheduleApi schedules = mock(WorkScheduleApi.class);
    private final IdentityApi identity = mock(IdentityApi.class);
    private final AttendanceService service = new AttendanceService(events, records, schedules, identity, 5);
    private final UUID employee = UUID.randomUUID();
    private final UUID scheduleId = UUID.randomUUID();
    private final MockMultipartFile image = new MockMultipartFile("photo", "timemark.jpg", "image/jpeg",
        new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1});

    @BeforeEach void person() {
        when(identity.person(employee)).thenReturn(new IdentityApi.Person(employee, "Employee", "EMPLOYEE", false, null));
    }
    @Test void missingFreshLocationCannotRecord() {
        var error = assertThrows(AttendanceException.class, () -> service.record(employee, scheduleId,
            EventType.CHECK_IN, null, 10.0, 106.0, 5.0, image));
        assertEquals("INVALID_ATTENDANCE", error.getCode());
        verifyNoInteractions(schedules, events);
    }
    @Test void otherEmployeesScheduleCannotRecord() {
        when(schedules.lock(scheduleId)).thenReturn(Optional.of(new WorkScheduleApi.Shift(scheduleId, UUID.randomUUID(),
            LocalDate.now(), "Shift", Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600))));
        var error = assertThrows(AttendanceException.class, () -> service.record(employee, scheduleId,
            EventType.CHECK_IN, Instant.now(), 10.0, 106.0, 5.0, image));
        assertEquals("FORBIDDEN", error.getCode());
        verifyNoInteractions(events);
    }
    @Test void checkOutNeedsCheckInAndDuplicateCheckInIsRejected() {
        when(schedules.lock(scheduleId)).thenReturn(Optional.of(new WorkScheduleApi.Shift(scheduleId, employee,
            LocalDate.now(), "Shift", Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600))));
        var out = assertThrows(AttendanceException.class, () -> service.record(employee, scheduleId,
            EventType.CHECK_OUT, Instant.now(), 10.0, 106.0, 5.0, image));
        assertEquals("CHECK_IN_REQUIRED", out.getCode());
        when(events.exists(scheduleId, EventType.CHECK_IN)).thenReturn(true);
        var duplicate = assertThrows(AttendanceException.class, () -> service.record(employee, scheduleId,
            EventType.CHECK_IN, Instant.now(), 10.0, 106.0, 5.0, image));
        assertEquals("ALREADY_RECORDED", duplicate.getCode());
        verify(events, never()).insert(any(), any(), any(), any(), any(), any(), anyDouble(), anyDouble(), anyDouble(), any(), any());
    }
}
