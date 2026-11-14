package com.company.hr.attendance.shiftpass;

import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.notification.NotificationApi;
import com.company.hr.attendance.shiftpass.api.ShiftPassModels.CreateShiftPassRequest;
import com.company.hr.attendance.shiftpass.application.ShiftPassException;
import com.company.hr.attendance.shiftpass.application.ShiftPassService;
import com.company.hr.attendance.shiftpass.domain.ShiftPass;
import com.company.hr.attendance.shiftpass.domain.ShiftPassNotification;
import com.company.hr.attendance.shiftpass.domain.ShiftPassStatus;
import com.company.hr.attendance.shiftpass.infrastructure.ShiftPassNotificationRepository;
import com.company.hr.attendance.shiftpass.infrastructure.ShiftPassRepository;
import com.company.hr.attendance.workschedule.WorkScheduleApi;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ShiftPassServiceTests {
    private ShiftPassRepository passes;
    private ShiftPassNotificationRepository notifications;
    private WorkScheduleApi workSchedule;
    private IdentityApi identity;
    private NotificationApi notificationApi;
    private Clock clock;
    private ShiftPassService service;

    @BeforeEach
    void setUp() {
        passes = mock(ShiftPassRepository.class);
        notifications = mock(ShiftPassNotificationRepository.class);
        workSchedule = mock(WorkScheduleApi.class);
        identity = mock(IdentityApi.class);
        notificationApi = mock(NotificationApi.class);
        clock = Clock.fixed(Instant.parse("2026-11-20T06:00:00Z"), ZoneOffset.UTC);
        service = new ShiftPassService(passes, notifications, workSchedule, identity, notificationApi, clock);
        when(passes.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void postPass_createsOpenPassAndPushesToSamePositionColleagues() {
        UUID requesterId = UUID.randomUUID();
        UUID colleague1 = UUID.randomUUID();
        UUID colleague2 = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();

        when(identity.lockPerson(requesterId))
            .thenReturn(new IdentityApi.Person(requesterId, "Nguyễn Văn An", "EMPLOYEE", false, UUID.randomUUID()));
        when(workSchedule.findDetail(scheduleId)).thenReturn(Optional.of(new WorkScheduleApi.ShiftDetail(
            scheduleId,
            requesterId,
            UUID.randomUUID(),
            LocalDate.of(2026, 11, 21),
            "Ca sáng",
            Instant.parse("2026-11-21T01:00:00Z"),
            Instant.parse("2026-11-21T05:00:00Z"),
            0,
            positionId,
            "Thu ngân"
        )));
        when(passes.existsByScheduleIdAndStatus(scheduleId, ShiftPassStatus.OPEN)).thenReturn(false);
        when(identity.activeEmployeeIdsByPosition(positionId)).thenReturn(List.of(requesterId, colleague1, colleague2));

        var response = service.postPass(requesterId, new CreateShiftPassRequest(scheduleId, "Bận việc gia đình"));

        assertThat(response.status()).isEqualTo(ShiftPassStatus.OPEN);
        assertThat(response.requesterName()).isEqualTo("Nguyễn Văn An");
        assertThat(response.claimerId()).isNull();
        assertThat(response.notifiedCount()).isEqualTo(2);

        ArgumentCaptor<List<ShiftPassNotification>> notifCaptor = ArgumentCaptor.forClass(List.class);
        verify(notifications).saveAll(notifCaptor.capture());
        assertThat(notifCaptor.getValue())
            .extracting(ShiftPassNotification::getRecipientId)
            .containsExactlyInAnyOrder(colleague1, colleague2);

        verify(notificationApi).pushToUsers(
            eq(List.of(colleague1, colleague2)),
            contains("Ca sáng"),
            contains("Thu ngân"),
            any(),
            any(),
            eq(Instant.parse("2026-11-21T01:00:00Z"))
        );
    }

    @Test
    void postPass_rejectsStartedOrForeignOrDuplicateOpenShift() {
        UUID requesterId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        when(identity.lockPerson(requesterId))
            .thenReturn(new IdentityApi.Person(requesterId, "An", "EMPLOYEE", false, UUID.randomUUID()));

        // Foreign schedule
        when(workSchedule.findDetail(scheduleId)).thenReturn(Optional.of(new WorkScheduleApi.ShiftDetail(
            scheduleId, otherId, UUID.randomUUID(), LocalDate.of(2026, 11, 21),
            "Ca sáng", Instant.parse("2026-11-21T01:00:00Z"), Instant.parse("2026-11-21T05:00:00Z"),
            0, UUID.randomUUID(), "Phục vụ"
        )));
        assertThatThrownBy(() -> service.postPass(requesterId, new CreateShiftPassRequest(scheduleId, "Lý do")))
            .isInstanceOf(AccessDeniedException.class);

        // Already started
        when(workSchedule.findDetail(scheduleId)).thenReturn(Optional.of(new WorkScheduleApi.ShiftDetail(
            scheduleId, requesterId, UUID.randomUUID(), LocalDate.of(2026, 11, 20),
            "Ca sáng", Instant.parse("2026-11-20T05:00:00Z"), Instant.parse("2026-11-20T09:00:00Z"),
            0, UUID.randomUUID(), "Phục vụ"
        )));
        assertThatThrownBy(() -> service.postPass(requesterId, new CreateShiftPassRequest(scheduleId, "Lý do")))
            .isInstanceOf(ShiftPassException.class);

        // Duplicate OPEN pass
        when(workSchedule.findDetail(scheduleId)).thenReturn(Optional.of(new WorkScheduleApi.ShiftDetail(
            scheduleId, requesterId, UUID.randomUUID(), LocalDate.of(2026, 11, 21),
            "Ca sáng", Instant.parse("2026-11-21T01:00:00Z"), Instant.parse("2026-11-21T05:00:00Z"),
            0, UUID.randomUUID(), "Phục vụ"
        )));
        when(passes.existsByScheduleIdAndStatus(scheduleId, ShiftPassStatus.OPEN)).thenReturn(true);
        assertThatThrownBy(() -> service.postPass(requesterId, new CreateShiftPassRequest(scheduleId, "Lý do")))
            .isInstanceOf(ShiftPassException.class);
    }

    @Test
    void cancelAndAutoExpire_closeOpenNotificationsAndPreserveShiftOwnership() {
        UUID requesterId = UUID.randomUUID();
        when(identity.person(requesterId))
            .thenReturn(new IdentityApi.Person(requesterId, "An", "EMPLOYEE", false, UUID.randomUUID()));

        var stalePass = new ShiftPass(
            UUID.randomUUID(), requesterId, LocalDate.of(2026, 11, 20),
            "Ca cũ", Instant.parse("2026-11-20T05:00:00Z"), Instant.parse("2026-11-20T09:00:00Z"),
            0, null, "Thu ngân", "Xin pass", Instant.parse("2026-11-19T00:00:00Z")
        );
        when(passes.findByRequesterIdOrClaimerIdOrderByCreatedAtDesc(requesterId, requesterId))
            .thenReturn(List.of(stalePass));
        when(identity.names(any())).thenReturn(Map.of(requesterId, "An"));

        var list = service.myPasses(requesterId);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).status()).isEqualTo(ShiftPassStatus.EXPIRED);
        verify(notifications).closeOpenByPassId(stalePass.getId(), clock.instant());

        var openPass = new ShiftPass(
            UUID.randomUUID(), requesterId, LocalDate.of(2026, 11, 22),
            "Ca tối", Instant.parse("2026-11-22T10:00:00Z"), Instant.parse("2026-11-22T14:00:00Z"),
            0, null, "Thu ngân", "Xin pass", Instant.parse("2026-11-20T01:00:00Z")
        );
        when(passes.lockById(openPass.getId())).thenReturn(Optional.of(openPass));

        service.cancelPass(requesterId, openPass.getId());
        assertThat(openPass.getStatus()).isEqualTo(ShiftPassStatus.CANCELLED);
        verify(notifications).closeOpenByPassId(openPass.getId(), clock.instant());
    }
}
