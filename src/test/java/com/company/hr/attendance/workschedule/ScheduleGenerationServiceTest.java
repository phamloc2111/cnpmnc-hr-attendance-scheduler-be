package com.company.hr.attendance.workschedule;

import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.organization.PositionsApi;
import com.company.hr.attendance.priority.PriorityService;
import com.company.hr.attendance.registration.infrastructure.RegistrationRepository;
import com.company.hr.attendance.workschedule.api.ScheduleModels.GenerateDraft;
import com.company.hr.attendance.workschedule.api.ScheduleModels.DraftSlot;
import com.company.hr.attendance.workschedule.application.ScheduleException;
import com.company.hr.attendance.workschedule.application.ScheduleGenerationService;
import com.company.hr.attendance.workschedule.domain.ShiftTemplate;
import com.company.hr.attendance.workschedule.domain.WorkSchedule;
import com.company.hr.attendance.workschedule.infrastructure.ShiftTemplateRepository;
import com.company.hr.attendance.workschedule.infrastructure.ScheduleDraftWeekRepository;
import com.company.hr.attendance.workschedule.infrastructure.ScheduleDraftSlotRepository;
import com.company.hr.attendance.workschedule.infrastructure.WorkScheduleRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ScheduleGenerationServiceTest {
    private static final LocalDate WEEK = LocalDate.of(2026, 11, 16);
    private final IdentityApi identity = mock(IdentityApi.class);
    private final PositionsApi positions = mock(PositionsApi.class);
    private final ShiftTemplateRepository templates = mock(ShiftTemplateRepository.class);
    private final WorkScheduleRepository schedules = mock(WorkScheduleRepository.class);
    private final ScheduleDraftWeekRepository draftWeeks = mock(ScheduleDraftWeekRepository.class);
    private final ScheduleDraftSlotRepository draftSlots = mock(ScheduleDraftSlotRepository.class);
    private final RegistrationRepository registrations = mock(RegistrationRepository.class);
    private final PriorityService priority = mock(PriorityService.class);
    private final UUID manager = UUID.randomUUID();
    private final UUID anna = UUID.randomUUID();
    private final UUID binh = UUID.randomUUID();
    private final UUID position = UUID.randomUUID();
    private final ShiftTemplate shift = new ShiftTemplate("Ca sáng", LocalTime.of(8, 0), LocalTime.of(12, 0),
        0, manager, 1, "Phục vụ", position, false);
    private ScheduleGenerationService service;

    @BeforeEach
    void setUp() {
        service = new ScheduleGenerationService(identity, positions, templates, schedules, draftWeeks, draftSlots, registrations, priority,
            "Asia/Ho_Chi_Minh", 480, 0);
        when(identity.person(manager)).thenReturn(new IdentityApi.Person(manager, "Manager", "MANAGER", false, null));
        when(identity.lockPerson(manager)).thenReturn(new IdentityApi.Person(manager, "Manager", "MANAGER", false, null));
        when(identity.managedEmployeeIds(manager)).thenReturn(List.of(anna, binh));
        when(identity.lockPerson(anna)).thenReturn(new IdentityApi.Person(anna, "Anna", "EMPLOYEE", false, manager));
        when(identity.lockPerson(binh)).thenReturn(new IdentityApi.Person(binh, "Binh", "EMPLOYEE", false, manager));
        when(identity.scoreProfiles(any())).thenReturn(Map.of(
            anna, profile(anna, "Anna"), binh, profile(binh, "Binh")));
        when(templates.findAllById(any())).thenReturn(List.of(shift));
        when(templates.findAll()).thenReturn(List.of(shift));
        when(positions.activeIds(any())).thenReturn(Set.of(position));
        when(registrations.selections(eq(WEEK), any())).thenReturn(Map.of(
            anna, Set.of(shift.getId()), binh, Set.of(shift.getId())));
        when(schedules.findByEmployeeIdInAndStartsAtLessThanAndEndsAtGreaterThan(any(), any(), any()))
            .thenReturn(List.of());
        when(priority.team(manager, manager, WEEK, position)).thenReturn(new PriorityService.Team(manager, WEEK,
            position, PriorityService.Weights.defaults(), List.of(score(anna, "Anna", 90), score(binh, "Binh", 80))));
    }

    @Test
    void createsOnlyRegisteredQualifiedHighestPriorityEmployee() {
        var result = service.generateDraft(manager, request());
        assertThat(result.createdCount()).isPositive();
        assertThat(result.items().get(0).employeeId()).isEqualTo(anna);
        assertThat(result.items()).allMatch(item -> item.draft());
        verify(schedules).saveAllAndFlush(any());
    }

    @Test
    void alreadyFilledPositionDoesNotCreateDuplicateSchedule() {
        when(schedules.findByEmployeeIdInAndStartsAtLessThanAndEndsAtGreaterThan(any(), any(), any()))
            .thenReturn(List.of(new WorkSchedule(anna, shift, WEEK, java.time.ZoneId.of("Asia/Ho_Chi_Minh"), manager)));

        var result = service.generateDraft(manager, request());
        assertThat(result.items()).noneMatch(item -> item.workDate().equals(WEEK));
    }

    @Test
    void noRegistrationCannotBeTreatedAsAvailability() {
        when(registrations.selections(eq(WEEK), any())).thenReturn(Map.of());

        var result = service.generateDraft(manager, request());
        assertThat(result.missingCount()).isEqualTo(7);
        verify(schedules, never()).saveAllAndFlush(any());
    }

    @Test
    void registeredEmployeeWithoutRequiredPositionIsNotAssigned() {
        when(identity.scoreProfiles(any())).thenReturn(Map.of(
            anna, new IdentityApi.ScoreProfile(anna, "Anna", false, manager,
                Instant.parse("2025-01-01T00:00:00Z"), UUID.randomUUID(), List.of()),
            binh, new IdentityApi.ScoreProfile(binh, "Binh", false, manager,
                Instant.parse("2025-01-01T00:00:00Z"), UUID.randomUUID(), List.of())));

        var result = service.generateDraft(manager, request());
        assertThat(result.missingCount()).isEqualTo(7);
        verify(schedules, never()).saveAllAndFlush(any());
    }

    @Test
    void unconfiguredWeeklyMaximumPreventsGeneration() {
        service = new ScheduleGenerationService(identity, positions, templates, schedules, draftWeeks, draftSlots, registrations, priority,
            "Asia/Ho_Chi_Minh", 0, 0);
        assertThatThrownBy(() -> service.generateDraft(manager, request()))
            .isInstanceOfSatisfying(ScheduleException.class, error ->
                assertThat(error.getCode()).isEqualTo("SCHEDULING_POLICY_NOT_CONFIGURED"));
        verify(schedules, never()).saveAllAndFlush(any());
    }

    @Test
    void weeklyMinimumNeverForcesInvalidAssignments() {
        service = new ScheduleGenerationService(identity, positions, templates, schedules, draftWeeks, draftSlots, registrations, priority,
            "Asia/Ho_Chi_Minh", 480, 300);

        var result = service.generateDraft(manager, request());
        assertThat(result.createdCount()).isPositive();
        assertThat(result.missingCount()).isPositive();
    }

    @Test
    void managerCannotGenerateForAnotherTeam() {
        var request = new GenerateDraft(WEEK, UUID.randomUUID());
        assertThatThrownBy(() -> service.generateDraft(manager, request))
            .isInstanceOfSatisfying(ScheduleException.class, error -> assertThat(error.getCode()).isEqualTo("FORBIDDEN"));
        verify(schedules, never()).saveAllAndFlush(any());
    }

    @Test
    void weeklyDraftKeepsFilledSeatsAndReportsTheRemainingSeats() {
        var demanding = new ShiftTemplate("Ca sáng", LocalTime.of(8, 0), LocalTime.of(12, 0),
            0, manager, 2, "Phục vụ", position, false);
        when(templates.findAll()).thenReturn(List.of(demanding));
        when(templates.findAllById(any())).thenReturn(List.of(demanding));
        when(registrations.selections(eq(WEEK), any())).thenReturn(Map.of(anna, Set.of(demanding.getId())));

        var result = service.generateDraft(manager, new GenerateDraft(WEEK, null));

        assertThat(result.createdCount()).isEqualTo(2);
        assertThat(result.missingCount()).isEqualTo(12);
        assertThat(result.shortages()).hasSize(7);
        assertThat(result.items()).allMatch(item -> item.draft());
        verify(schedules).saveAllAndFlush(any());
        verify(draftWeeks).createIfAbsent(any(), eq(manager), eq(WEEK));
    }

    @Test
    void noAvailableEmployeeStillReturnsShortagesWithoutSaving() {
        when(registrations.selections(eq(WEEK), any())).thenReturn(Map.of());

        var result = service.generateDraft(manager, new GenerateDraft(WEEK, null));

        assertThat(result.createdCount()).isZero();
        assertThat(result.missingCount()).isEqualTo(7);
        verify(schedules, never()).saveAllAndFlush(any());
        verify(draftWeeks).createIfAbsent(any(), eq(manager), eq(WEEK));
    }

    @Test
    void onlySelectedDayShiftIsPlannedAndSavedForWarnings() {
        var slot = new DraftSlot(WEEK.plusDays(2), shift.getId());
        when(registrations.selections(eq(WEEK), any())).thenReturn(Map.of());

        var result = service.generateDraft(manager, new GenerateDraft(WEEK, null, List.of(slot)));

        assertThat(result.createdCount()).isZero();
        assertThat(result.missingCount()).isEqualTo(1);
        assertThat(result.shortages()).extracting(item -> item.workDate()).containsExactly(WEEK.plusDays(2));
        verify(draftSlots).replace(manager, WEEK, List.of(slot));
    }

    @Test
    void duplicateOrOutOfWeekSlotsAreRejectedBeforeSaving() {
        var slot = new DraftSlot(WEEK, shift.getId());
        for (var slots : List.of(List.of(slot, slot), List.of(new DraftSlot(WEEK.plusDays(7), shift.getId())))) {
            assertThatThrownBy(() -> service.generateDraft(manager, new GenerateDraft(WEEK, null, slots)))
                .isInstanceOfSatisfying(ScheduleException.class,
                    error -> assertThat(error.getCode()).isEqualTo("INVALID_GENERATION_REQUEST"));
        }
        verifyNoInteractions(draftSlots);
        verify(schedules, never()).saveAllAndFlush(any());
    }

    @Test
    void selectedTemplateWithInactivePositionIsRejectedBeforeSaving() {
        when(positions.activeIds(any())).thenReturn(Set.of());
        var slot = new DraftSlot(WEEK, shift.getId());

        assertThatThrownBy(() -> service.generateDraft(manager, new GenerateDraft(WEEK, null, List.of(slot))))
            .isInstanceOfSatisfying(ScheduleException.class,
                error -> assertThat(error.getCode()).isEqualTo("SHIFT_NOT_SCHEDULABLE"));
        verifyNoInteractions(draftSlots);
        verify(schedules, never()).saveAllAndFlush(any());
    }

    private GenerateDraft request() {
        return new GenerateDraft(WEEK, null);
    }

    private IdentityApi.ScoreProfile profile(UUID id, String name) {
        return new IdentityApi.ScoreProfile(id, name, false, manager, Instant.parse("2025-01-01T00:00:00Z"),
            position, List.of());
    }

    private PriorityService.Score score(UUID id, String name, int total) {
        return new PriorityService.Score(id, name, total, 90, 90, 50, 100, 60, 0, 0, 0, 0);
    }
}
