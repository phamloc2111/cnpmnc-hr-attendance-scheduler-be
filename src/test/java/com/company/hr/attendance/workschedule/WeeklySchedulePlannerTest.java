package com.company.hr.attendance.workschedule;

import com.company.hr.attendance.workschedule.application.WeeklySchedulePlanner;
import com.company.hr.attendance.workschedule.application.WeeklySchedulePlanner.Existing;
import com.company.hr.attendance.workschedule.application.WeeklySchedulePlanner.Slot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklySchedulePlannerTest {
    private static final LocalDate WEEK = LocalDate.of(2026, 11, 16);
    private final UUID template = UUID.randomUUID();
    private final UUID anna = UUID.randomUUID();
    private final UUID binh = UUID.randomUUID();

    @Test
    void higherScoreWinsWhenBothCanWork() {
        var assignment = plan(List.of(slot(0, 8, 4, Map.of(anna, 90, binh, 80))), List.of(), 480);
        assertThat(assignment).extracting(WeeklySchedulePlanner.Assignment::employeeId).containsExactly(anna);
    }

    @Test
    void equalScorePrefersFewerMinutesAlreadyScheduledThisWeek() {
        var existing = new Existing(anna, WEEK, at(0, 1), at(0, 3), 120);
        var assignment = plan(List.of(slot(1, 8, 4, Map.of(anna, 90, binh, 90))), List.of(existing), 480);
        assertThat(assignment).extracting(WeeklySchedulePlanner.Assignment::employeeId).containsExactly(binh);
    }

    @Test
    void neverOverlapsEvenWithPriorWeekOvernightShift() {
        var overnight = new Existing(anna, WEEK.minusDays(1), at(-1, 22), at(0, 9), 600);
        var assignment = plan(List.of(slot(0, 8, 4, Map.of(anna, 100, binh, 80))), List.of(overnight), 480);
        assertThat(assignment).extracting(WeeklySchedulePlanner.Assignment::employeeId).containsExactly(binh);
    }

    @Test
    void weeklyMaximumUsesWorkingMinutesAfterBreaks() {
        var existing = new Existing(anna, WEEK, at(0, 1), at(0, 4), 180);
        var assignment = plan(List.of(slot(1, 8, 4, Map.of(anna, 100, binh, 80))), List.of(existing), 400);
        assertThat(assignment).extracting(WeeklySchedulePlanner.Assignment::employeeId).containsExactly(binh);
    }

    @Test
    void backtracksToFillScarcePositionRatherThanPersistingPartialSchedule() {
        var common = slot(0, 8, 4, Map.of(anna, 100, binh, 80));
        var scarce = slot(1, 8, 4, Map.of(anna, 100));
        var result = plan(List.of(common, scarce), List.of(), 480);
        assertThat(result).hasSize(2);
        assertThat(result).filteredOn(a -> a.slot() == scarce)
            .extracting(WeeklySchedulePlanner.Assignment::employeeId).containsExactly(anna);
    }

    @Test
    void impossibleDemandReturnsNoAssignments() {
        var planner = new WeeklySchedulePlanner(WEEK, 480,
            List.of(slot(0, 8, 4, Map.of(anna, 90)), slot(0, 8, 4, Map.of(anna, 90))), List.of());
        assertThat(planner.plan()).isEmpty();
    }

    private List<WeeklySchedulePlanner.Assignment> plan(List<Slot> slots, List<Existing> existing, long max) {
        return new WeeklySchedulePlanner(WEEK, max, slots, existing).plan().orElseThrow();
    }

    private Slot slot(int day, int hour, int duration, Map<UUID, Integer> scores) {
        return new Slot(template, WEEK.plusDays(day), at(day, hour), at(day, hour + duration), duration * 60, scores);
    }

    private Instant at(int day, int hour) {
        return WEEK.plusDays(day).atStartOfDay(java.time.ZoneOffset.UTC).plusHours(hour).toInstant();
    }
}
