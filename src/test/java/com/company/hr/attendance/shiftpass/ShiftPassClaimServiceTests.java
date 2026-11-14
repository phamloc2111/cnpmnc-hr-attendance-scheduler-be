package com.company.hr.attendance.shiftpass;

import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.notification.NotificationApi;
import com.company.hr.attendance.shiftpass.application.ShiftPassException;
import com.company.hr.attendance.shiftpass.application.ShiftPassService;
import com.company.hr.attendance.shiftpass.domain.ShiftPass;
import com.company.hr.attendance.shiftpass.domain.ShiftPassNotification;
import com.company.hr.attendance.shiftpass.domain.ShiftPassStatus;
import com.company.hr.attendance.shiftpass.infrastructure.ShiftPassAuditRecorder;
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
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ShiftPassClaimServiceTests {
    private ShiftPassRepository passes;
    private ShiftPassNotificationRepository notifications;
    private WorkScheduleApi workSchedule;
    private IdentityApi identity;
    private NotificationApi notificationApi;
    private ShiftPassAuditRecorder auditRecorder;
    private Clock clock;
    private ShiftPassService service;

    @BeforeEach
    void setUp() {
        passes = mock(ShiftPassRepository.class);
        notifications = mock(ShiftPassNotificationRepository.class);
        workSchedule = mock(WorkScheduleApi.class);
        identity = mock(IdentityApi.class);
        notificationApi = mock(NotificationApi.class);
        auditRecorder = mock(ShiftPassAuditRecorder.class);
        clock = Clock.fixed(Instant.parse("2026-11-16T00:00:00Z"), ZoneOffset.UTC);
        service = new ShiftPassService(passes, notifications, workSchedule, identity, notificationApi, auditRecorder, clock);
        when(passes.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void availablePasses_marksOverlapAndOver48HourWeeklyCapAsIneligible() {
        UUID candidateId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();

        when(identity.person(candidateId))
            .thenReturn(new IdentityApi.Person(candidateId, "Trần Bình", "EMPLOYEE", false, UUID.randomUUID()));
        when(identity.scoreProfiles(List.of(candidateId))).thenReturn(Map.of(
            candidateId,
            new IdentityApi.ScoreProfile(candidateId, "Trần Bình", false, UUID.randomUUID(), clock.instant(), positionId, List.of())
        ));
        when(identity.names(any())).thenReturn(Map.of(requesterId, "Nguyễn An"));

        // Week 1 (2026-11-16 .. 2026-11-22): candidate already has 8h on Nov 17
        var existingShiftWeek1 = new WorkScheduleApi.Shift(
            UUID.randomUUID(), candidateId, LocalDate.of(2026, 11, 17),
            "Ca hiện có", Instant.parse("2026-11-17T08:00:00Z"), Instant.parse("2026-11-17T16:00:00Z"), 0
        );
        // Week 2 (2026-11-23 .. 2026-11-29): candidate already has 46h (6 shifts summing 2760 mins)
        var existingShiftWeek2 = new WorkScheduleApi.Shift(
            UUID.randomUUID(), candidateId, LocalDate.of(2026, 11, 24),
            "Ca tuần 2", Instant.parse("2026-11-24T00:00:00Z"), Instant.parse("2026-11-25T22:00:00Z"), 0
        ); // 46h = 2760 minutes

        when(workSchedule.employeeShifts(eq(List.of(candidateId)), any(), any()))
            .thenReturn(List.of(existingShiftWeek1, existingShiftWeek2));

        // Pass 1: eligible (Nov 18, no overlap, week 1 total becomes 12h <= 48h)
        var passEligible = new ShiftPass(
            UUID.randomUUID(), requesterId, LocalDate.of(2026, 11, 18),
            "Ca hợp lệ", Instant.parse("2026-11-18T08:00:00Z"), Instant.parse("2026-11-18T12:00:00Z"),
            0, positionId, "Thu ngân", null, clock.instant()
        );
        // Pass 2: overlaps existingShiftWeek1 on Nov 17
        var passOverlap = new ShiftPass(
            UUID.randomUUID(), requesterId, LocalDate.of(2026, 11, 17),
            "Ca trùng giờ", Instant.parse("2026-11-17T10:00:00Z"), Instant.parse("2026-11-17T14:00:00Z"),
            0, positionId, "Thu ngân", null, clock.instant()
        );
        // Pass 3: Week 2 (Nov 26, 4h = 240 mins -> 46h + 4h = 50h > 48h)
        var passOverCap = new ShiftPass(
            UUID.randomUUID(), requesterId, LocalDate.of(2026, 11, 26),
            "Ca vượt 48h", Instant.parse("2026-11-26T08:00:00Z"), Instant.parse("2026-11-26T12:00:00Z"),
            0, positionId, "Thu ngân", null, clock.instant()
        );

        when(passes.findByStatusOrderByStartsAtAsc(ShiftPassStatus.OPEN))
            .thenReturn(List.of(passEligible, passOverlap, passOverCap));

        var available = service.availablePasses(candidateId);
        assertThat(available).hasSize(3);

        assertThat(available.get(0).eligible()).isTrue();
        assertThat(available.get(0).ineligibleReason()).isNull();

        assertThat(available.get(1).eligible()).isFalse();
        assertThat(available.get(1).ineligibleReason()).contains("Trùng giờ");

        assertThat(available.get(2).eligible()).isFalse();
        assertThat(available.get(2).ineligibleReason()).contains("48 giờ/tuần");
    }

    @Test
    void claimPass_succeedsForFirstClaimerClosesNotificationsNotifiesManagerWritesAuditAndBlocksSecondClaimer() {
        UUID claimer1 = UUID.randomUUID();
        UUID claimer2 = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();

        var pass = new ShiftPass(
            scheduleId, requesterId, LocalDate.of(2026, 11, 20),
            "Ca tối", Instant.parse("2026-11-20T11:00:00Z"), Instant.parse("2026-11-20T15:00:00Z"),
            0, positionId, "Thu ngân", "Bận việc", clock.instant()
        );

        when(identity.lockPerson(claimer1))
            .thenReturn(new IdentityApi.Person(claimer1, "Trần Văn Bình", "EMPLOYEE", false, managerId));
        when(identity.lockPerson(claimer2))
            .thenReturn(new IdentityApi.Person(claimer2, "Lê Thị Chi", "EMPLOYEE", false, managerId));
        when(identity.person(requesterId))
            .thenReturn(new IdentityApi.Person(requesterId, "Nguyễn Văn An", "EMPLOYEE", false, managerId));
        when(identity.scoreProfiles(List.of(claimer1))).thenReturn(Map.of(
            claimer1,
            new IdentityApi.ScoreProfile(claimer1, "Trần Văn Bình", false, managerId, clock.instant(), positionId, List.of())
        ));
        when(passes.lockById(pass.getId())).thenReturn(Optional.of(pass));
        when(workSchedule.employeeShifts(eq(List.of(claimer1)), any(), any())).thenReturn(List.of());
        when(workSchedule.transferShift(scheduleId, requesterId, claimer1)).thenReturn(true);

        var claimed = service.claimPass(claimer1, pass.getId());

        assertThat(claimed.status()).isEqualTo(ShiftPassStatus.CLAIMED);
        assertThat(claimed.claimerId()).isEqualTo(claimer1);
        assertThat(claimed.claimerName()).isEqualTo("Trần Văn Bình");

        verify(workSchedule).transferShift(scheduleId, requesterId, claimer1);
        verify(notifications).closeOpenByPassId(pass.getId(), clock.instant());

        ArgumentCaptor<List<ShiftPassNotification>> notifCaptor = ArgumentCaptor.forClass(List.class);
        verify(notifications).saveAll(notifCaptor.capture());
        assertThat(notifCaptor.getValue())
            .extracting(ShiftPassNotification::getRecipientId, ShiftPassNotification::getKind)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(requesterId, "PASS_CLAIMED_OWNER"),
                org.assertj.core.groups.Tuple.tuple(managerId, "PASS_CLAIMED_MANAGER")
            );

        verify(auditRecorder).recordClaim(
            eq(claimer1),
            eq(requesterId),
            eq(LocalDate.of(2026, 11, 20)),
            contains("Ca tối"),
            contains("Nguyễn Văn An"),
            contains("Trần Văn Bình"),
            eq(clock.instant())
        );

        // Concurrent second claimer must fail with 409 Conflict
        assertThatThrownBy(() -> service.claimPass(claimer2, pass.getId()))
            .isInstanceOf(ShiftPassException.class)
            .extracting(ex -> ((ShiftPassException) ex).getStatus())
            .isEqualTo(HttpStatus.CONFLICT);
    }
}
