package com.company.hr.attendance.attendance;

import com.company.hr.attendance.attendance.api.AttendanceModels.EventType;
import com.company.hr.attendance.attendance.api.AttendanceModels.TimemarkAnalysisResponse;
import com.company.hr.attendance.attendance.api.AttendanceModels.TimemarkEvidence;
import com.company.hr.attendance.attendance.application.AttendanceException;
import com.company.hr.attendance.attendance.application.AttendanceService;
import com.company.hr.attendance.attendance.application.AttendanceVerificationService;
import com.company.hr.attendance.attendance.application.TimemarkReader;
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
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttendanceServiceTest {
    private final AttendanceEventRepository events = mock(AttendanceEventRepository.class);
    private final AttendanceRecordRepository records = mock(AttendanceRecordRepository.class);
    private final WorkScheduleApi schedules = mock(WorkScheduleApi.class);
    private final IdentityApi identity = mock(IdentityApi.class);
    private final TimemarkReader timemarkReader = mock(TimemarkReader.class);
    private final AttendanceVerificationService verification = new AttendanceVerificationService(
        () -> Optional.of(new com.company.hr.attendance.organization.WorkplaceApi.Configuration(10.0, 106.0, 100, 30, 0, null, null)), 300);
    private final AttendanceService service = new AttendanceService(
        events, records, schedules, identity, timemarkReader, verification, 5);
    private final UUID employee = UUID.randomUUID();
    private final UUID scheduleId = UUID.randomUUID();
    private final MockMultipartFile image = new MockMultipartFile("photo", "timemark.jpg", "image/jpeg",
        new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1});

    @BeforeEach void person() {
        when(identity.person(employee)).thenReturn(new IdentityApi.Person(employee, "Employee", "EMPLOYEE", false, null));
    }

    @Test void validTimemarkPhoto_verifiesAndPersistsAttendance() {
        when(schedules.lock(scheduleId)).thenReturn(Optional.of(new WorkScheduleApi.Shift(scheduleId, employee,
            LocalDate.now(), "Shift", Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600))));
        when(timemarkReader.read(any())).thenReturn(
            new TimemarkAnalysisResponse(true, Instant.now().toString(), 10.0, 106.0, "TP.HCM", null));

        var response = service.record(employee, scheduleId, EventType.CHECK_IN, image);

        assertNotNull(response);
        assertEquals(scheduleId, response.scheduleId());
        assertEquals(EventType.CHECK_IN, response.type());
        assertEquals(10.0, response.latitude());
        assertEquals(106.0, response.longitude());
        verify(events).insert(any(), eq(scheduleId), eq(employee), eq(EventType.CHECK_IN), any(), any(), eq(10.0), eq(106.0), eq(0.0), eq("image/jpeg"), any());
        verify(records).saveAndFlush(any());
    }

    @Test void unreadableTimemarkPhoto_isRejectedWithoutPersisting() {
        when(timemarkReader.read(any())).thenReturn(
            new TimemarkAnalysisResponse(false, null, null, null, null, "Không đọc được chữ"));

        var error = assertThrows(AttendanceException.class, () ->
            service.record(employee, scheduleId, EventType.CHECK_IN, image));

        assertEquals("TIMEMARK_UNREADABLE", error.getCode());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatus());
        verifyNoInteractions(schedules, events, records);
    }

    @Test void jpegLabelWithInvalidBytes_isRejectedBeforeAiAndWithoutPersisting() {
        var mislabeled = new MockMultipartFile("photo", "timemark.jpg", "image/jpeg", "not a JPEG".getBytes());

        var error = assertThrows(AttendanceException.class, () ->
            service.record(employee, scheduleId, EventType.CHECK_IN, mislabeled));

        assertEquals("INVALID_ATTENDANCE", error.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        verifyNoInteractions(timemarkReader, schedules, events, records);
    }

    @Test void photoMissingTime_isRejectedWithoutPersisting() {
        when(timemarkReader.read(any())).thenReturn(
            new TimemarkAnalysisResponse(true, null, 10.0, 106.0, "TP.HCM", null));

        var error = assertThrows(AttendanceException.class, () ->
            service.record(employee, scheduleId, EventType.CHECK_IN, image));

        assertEquals("TIMEMARK_MISSING_TIME", error.getCode());
        verifyNoInteractions(schedules, events, records);
    }

    @Test void photoMissingCoordinates_isRejectedWithoutPersisting() {
        when(timemarkReader.read(any())).thenReturn(
            new TimemarkAnalysisResponse(true, Instant.now().toString(), null, null, "TP.HCM", null));

        var error = assertThrows(AttendanceException.class, () ->
            service.record(employee, scheduleId, EventType.CHECK_IN, image));

        assertEquals("TIMEMARK_MISSING_COORDINATES", error.getCode());
        verifyNoInteractions(schedules, events, records);
    }

    @Test void gpsOutsideWorkplaceRadius_isRejectedWithoutPersisting() {
        when(timemarkReader.read(any())).thenReturn(
            new TimemarkAnalysisResponse(true, Instant.now().toString(), 20.0, 106.0, "Hà Nội", null));

        var error = assertThrows(AttendanceException.class, () ->
            service.record(employee, scheduleId, EventType.CHECK_IN, image));

        assertEquals("ATTENDANCE_VERIFICATION_FAILED", error.getCode());
        verifyNoInteractions(schedules, events, records);
    }

    @Test void staleTimestamp_isRejectedWithoutPersisting() {
        when(timemarkReader.read(any())).thenReturn(
            new TimemarkAnalysisResponse(true, Instant.now().minusSeconds(600).toString(), 10.0, 106.0, "TP.HCM", null));

        var error = assertThrows(AttendanceException.class, () ->
            service.record(employee, scheduleId, EventType.CHECK_IN, image));

        assertEquals("ATTENDANCE_VERIFICATION_FAILED", error.getCode());
        verifyNoInteractions(schedules, events, records);
    }

    @Test void geminiAiFailure_propagatesWithoutRecording() {
        when(timemarkReader.read(any())).thenThrow(
            new AttendanceException(HttpStatus.BAD_GATEWAY, "TIMEMARK_AI_FAILED", "Gemini error"));

        var error = assertThrows(AttendanceException.class, () ->
            service.record(employee, scheduleId, EventType.CHECK_IN, image));

        assertEquals("TIMEMARK_AI_FAILED", error.getCode());
        verifyNoInteractions(schedules, events, records);
    }

    @Test void geminiTimeout_propagatesWithoutRecording() {
        when(timemarkReader.read(any())).thenThrow(
            new AttendanceException(HttpStatus.SERVICE_UNAVAILABLE, "TIMEMARK_AI_UNAVAILABLE", "Timeout"));

        var error = assertThrows(AttendanceException.class, () ->
            service.record(employee, scheduleId, EventType.CHECK_IN, image));

        assertEquals("TIMEMARK_AI_UNAVAILABLE", error.getCode());
        verifyNoInteractions(schedules, events, records);
    }

    @Test void geminiApiKeyNotConfigured_propagatesWithoutRecording() {
        when(timemarkReader.read(any())).thenThrow(
            new AttendanceException(HttpStatus.SERVICE_UNAVAILABLE, "TIMEMARK_AI_NOT_CONFIGURED", "No key"));

        var error = assertThrows(AttendanceException.class, () ->
            service.record(employee, scheduleId, EventType.CHECK_IN, image));

        assertEquals("TIMEMARK_AI_NOT_CONFIGURED", error.getCode());
        verifyNoInteractions(schedules, events, records);
    }

    @Test void spoofedClientParameters_doNotAffectVerificationOrRecordedTime() {
        when(schedules.lock(scheduleId)).thenReturn(Optional.of(new WorkScheduleApi.Shift(scheduleId, employee,
            LocalDate.now(), "Shift", Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600))));
        // Photo has valid time and coordinates
        var photoTime = Instant.now().minusSeconds(30);
        when(timemarkReader.read(any())).thenReturn(
            new TimemarkAnalysisResponse(true, photoTime.toString(), 10.0, 106.0, "TP.HCM", null));

        // Client passes spoofed GPS and timestamp in legacy overload
        var fakeClientTime = Instant.now().minusSeconds(3600);
        var response = service.record(employee, scheduleId, EventType.CHECK_IN,
            fakeClientTime, 99.0, 99.0, 1.0,
            new TimemarkEvidence(fakeClientTime, 99.0, 99.0), image);

        // Verification succeeded and saved coordinates from PHOTO, not spoofed client values
        assertEquals(10.0, response.latitude());
        assertEquals(106.0, response.longitude());
        verify(events).insert(any(), eq(scheduleId), eq(employee), eq(EventType.CHECK_IN), any(), eq(photoTime), eq(10.0), eq(106.0), eq(0.0), any(), any());
    }

    @Test void supportsWebpFormat() {
        when(schedules.lock(scheduleId)).thenReturn(Optional.of(new WorkScheduleApi.Shift(scheduleId, employee,
            LocalDate.now(), "Shift", Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600))));
        when(timemarkReader.read(any())).thenReturn(
            new TimemarkAnalysisResponse(true, Instant.now().toString(), 10.0, 106.0, "TP.HCM", null));

        byte[] webpBytes = new byte[]{0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50, 1, 2, 3};
        var webpImage = new MockMultipartFile("photo", "timemark.webp", "image/webp", webpBytes);

        var response = service.record(employee, scheduleId, EventType.CHECK_IN, webpImage);
        assertNotNull(response);
        verify(events).insert(any(), any(), any(), any(), any(), any(), anyDouble(), anyDouble(), anyDouble(), eq("image/webp"), any());
    }

    @Test void otherEmployeesScheduleCannotRecord() {
        when(schedules.lock(scheduleId)).thenReturn(Optional.of(new WorkScheduleApi.Shift(scheduleId, UUID.randomUUID(),
            LocalDate.now(), "Shift", Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600))));
        when(timemarkReader.read(any())).thenReturn(
            new TimemarkAnalysisResponse(true, Instant.now().toString(), 10.0, 106.0, "TP.HCM", null));

        var error = assertThrows(AttendanceException.class, () ->
            service.record(employee, scheduleId, EventType.CHECK_IN, image));

        assertEquals("FORBIDDEN", error.getCode());
        verifyNoInteractions(events);
    }

    @Test void checkOutNeedsCheckInAndDuplicateCheckInIsRejected() {
        when(schedules.lock(scheduleId)).thenReturn(Optional.of(new WorkScheduleApi.Shift(scheduleId, employee,
            LocalDate.now(), "Shift", Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600))));
        when(timemarkReader.read(any())).thenReturn(
            new TimemarkAnalysisResponse(true, Instant.now().toString(), 10.0, 106.0, "TP.HCM", null));

        var out = assertThrows(AttendanceException.class, () ->
            service.record(employee, scheduleId, EventType.CHECK_OUT, image));
        assertEquals("CHECK_IN_REQUIRED", out.getCode());

        when(events.exists(scheduleId, EventType.CHECK_IN)).thenReturn(true);
        var duplicate = assertThrows(AttendanceException.class, () ->
            service.record(employee, scheduleId, EventType.CHECK_IN, image));
        assertEquals("ALREADY_RECORDED", duplicate.getCode());

        verify(events, never()).insert(any(), any(), any(), any(), any(), any(), anyDouble(), anyDouble(), anyDouble(), any(), any());
    }
}
