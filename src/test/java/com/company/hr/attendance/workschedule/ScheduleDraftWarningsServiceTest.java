package com.company.hr.attendance.workschedule;

import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.organization.PositionsApi;
import com.company.hr.attendance.workschedule.api.ScheduleModels.DraftSlot;
import com.company.hr.attendance.workschedule.application.ScheduleDraftWarningsService;
import com.company.hr.attendance.workschedule.domain.ShiftTemplate;
import com.company.hr.attendance.workschedule.infrastructure.ScheduleDraftSlotRepository;
import com.company.hr.attendance.workschedule.infrastructure.ScheduleDraftWeekRepository;
import com.company.hr.attendance.workschedule.infrastructure.ShiftTemplateRepository;
import com.company.hr.attendance.workschedule.infrastructure.WorkScheduleRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ScheduleDraftWarningsServiceTest {
    @Test void shortagesOnlyIncludePersistedSelectedSlots() {
        var manager = UUID.randomUUID();
        var position = UUID.randomUUID();
        var week = LocalDate.of(2026, 11, 16);
        var shift = new ShiftTemplate("Ca sáng", LocalTime.of(8, 0), LocalTime.of(12, 0),
            0, manager, 2, "Phục vụ", position, false);
        var identity = mock(IdentityApi.class);
        var positions = mock(PositionsApi.class);
        var templates = mock(ShiftTemplateRepository.class);
        var schedules = mock(WorkScheduleRepository.class);
        var draftWeeks = mock(ScheduleDraftWeekRepository.class);
        var draftSlots = mock(ScheduleDraftSlotRepository.class);
        when(identity.person(manager)).thenReturn(new IdentityApi.Person(manager, "Manager", "MANAGER", false, null));
        when(identity.managedEmployeeIds(manager)).thenReturn(List.of());
        when(draftWeeks.existsByManagerIdAndWeekStart(manager, week)).thenReturn(true);
        when(templates.findAll()).thenReturn(List.of(shift));
        when(positions.activeIds(any())).thenReturn(Set.of(position));
        when(draftSlots.find(manager, week)).thenReturn(List.of(new DraftSlot(week.plusDays(3), shift.getId())));
        var service = new ScheduleDraftWarningsService(identity, positions, templates, schedules, draftWeeks, draftSlots, 2400);

        var result = service.warnings(manager, week, null);

        assertThat(result.missingCount()).isEqualTo(2);
        assertThat(result.shortages()).hasSize(1);
        assertThat(result.shortages().get(0).workDate()).isEqualTo(week.plusDays(3));
    }

    @Test void weekWithoutSelectedSlotsDoesNotInventShortages() {
        var manager = UUID.randomUUID();
        var week = LocalDate.of(2026, 11, 16);
        var identity = mock(IdentityApi.class);
        var draftWeeks = mock(ScheduleDraftWeekRepository.class);
        var draftSlots = mock(ScheduleDraftSlotRepository.class);
        when(identity.person(manager)).thenReturn(new IdentityApi.Person(manager, "Manager", "MANAGER", false, null));
        when(identity.managedEmployeeIds(manager)).thenReturn(List.of());
        when(draftWeeks.existsByManagerIdAndWeekStart(manager, week)).thenReturn(true);
        when(draftSlots.find(manager, week)).thenReturn(List.of());
        var service = new ScheduleDraftWarningsService(identity, mock(PositionsApi.class),
            mock(ShiftTemplateRepository.class), mock(WorkScheduleRepository.class), draftWeeks, draftSlots, 2400);

        var result = service.warnings(manager, week, null);

        assertThat(result.draftExists()).isTrue();
        assertThat(result.missingCount()).isZero();
        assertThat(result.shortages()).isEmpty();
    }
}
