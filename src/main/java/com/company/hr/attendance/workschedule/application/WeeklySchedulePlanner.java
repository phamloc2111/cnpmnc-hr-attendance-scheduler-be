package com.company.hr.attendance.workschedule.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Finds a complete weekly assignment before the caller writes any schedule rows. */
public final class WeeklySchedulePlanner {
    private static final int SEARCH_LIMIT = 20_000;

    public record Slot(UUID templateId, LocalDate workDate, Instant startsAt, Instant endsAt,
                       int workingMinutes, Map<UUID, Integer> eligibleScores) {}
    public record Existing(UUID employeeId, LocalDate workDate, Instant startsAt, Instant endsAt,
                           long workingMinutes) {}
    public record Assignment(Slot slot, UUID employeeId) {}
    public record PartialPlan(List<Assignment> assignments, List<Slot> unfilled) {}

    private final LocalDate weekStart;
    private final long maxWeeklyMinutes;
    private final List<Slot> slots;
    private final List<Assignment> assignments = new ArrayList<>();
    private final Map<UUID, Long> minutes = new HashMap<>();
    private final Map<UUID, List<Existing>> occupied = new HashMap<>();
    private int searched;
    private boolean searchExhausted;

    public WeeklySchedulePlanner(LocalDate weekStart, long maxWeeklyMinutes, List<Slot> slots, List<Existing> existing) {
        this.weekStart = weekStart;
        this.maxWeeklyMinutes = maxWeeklyMinutes;
        this.slots = List.copyOf(slots);
        for (var shift : existing) {
            occupied.computeIfAbsent(shift.employeeId(), ignored -> new ArrayList<>()).add(shift);
            if (!shift.workDate().isBefore(weekStart) && shift.workDate().isBefore(weekStart.plusWeeks(1))) {
                minutes.merge(shift.employeeId(), shift.workingMinutes(), Long::sum);
            }
        }
    }

    public Optional<List<Assignment>> plan() {
        if (maxWeeklyMinutes <= 0 || slots.size() > 200) throw new IllegalArgumentException("Invalid scheduling limits");
        return search(new HashSet<>()) ? Optional.of(List.copyOf(assignments)) : Optional.empty();
    }

    public boolean searchExhausted() { return searchExhausted; }

    /** Fast best-effort draft: scarce slots first, then highest score and fairest hours. */
    public PartialPlan planPartial() {
        if (maxWeeklyMinutes <= 0 || slots.size() > 700) throw new IllegalArgumentException("Invalid scheduling limits");
        var ordered = new ArrayList<>(slots);
        ordered.sort(Comparator.comparingInt((Slot slot) -> slot.eligibleScores().size())
            .thenComparing(Slot::workDate).thenComparing(Slot::startsAt).thenComparing(Slot::templateId));
        var unfilled = new ArrayList<Slot>();
        for (var slot : ordered) {
            var choices = eligible(slot);
            if (choices.isEmpty()) {
                unfilled.add(slot);
                continue;
            }
            var employeeId = choices.getFirst();
            occupied.computeIfAbsent(employeeId, ignored -> new ArrayList<>())
                .add(new Existing(employeeId, slot.workDate(), slot.startsAt(), slot.endsAt(), slot.workingMinutes()));
            minutes.merge(employeeId, (long) slot.workingMinutes(), Long::sum);
            assignments.add(new Assignment(slot, employeeId));
        }
        return new PartialPlan(List.copyOf(assignments), List.copyOf(unfilled));
    }

    private boolean search(Set<Integer> filled) {
        if (filled.size() == slots.size()) return true;
        if (++searched > SEARCH_LIMIT) { searchExhausted = true; return false; }
        int chosen = -1;
        List<UUID> choices = List.of();
        for (int index = 0; index < slots.size(); index++) {
            if (filled.contains(index)) continue;
            var eligible = eligible(slots.get(index));
            if (eligible.isEmpty()) return false;
            if (chosen < 0 || eligible.size() < choices.size()) {
                chosen = index;
                choices = eligible;
            }
        }
        var slot = slots.get(chosen);
        filled.add(chosen);
        for (var employeeId : choices) {
            var shift = new Existing(employeeId, slot.workDate(), slot.startsAt(), slot.endsAt(), slot.workingMinutes());
            occupied.computeIfAbsent(employeeId, ignored -> new ArrayList<>()).add(shift);
            minutes.merge(employeeId, (long) slot.workingMinutes(), Long::sum);
            assignments.add(new Assignment(slot, employeeId));
            if (search(filled)) return true;
            if (searchExhausted) return false;
            assignments.removeLast();
            minutes.merge(employeeId, -(long) slot.workingMinutes(), Long::sum);
            occupied.get(employeeId).removeLast();
        }
        filled.remove(chosen);
        return false;
    }

    private List<UUID> eligible(Slot slot) {
        return slot.eligibleScores().keySet().stream()
            .filter(employeeId -> minutes.getOrDefault(employeeId, 0L) + slot.workingMinutes() <= maxWeeklyMinutes)
            .filter(employeeId -> occupied.getOrDefault(employeeId, List.of()).stream().noneMatch(shift ->
                slot.startsAt().isBefore(shift.endsAt()) && slot.endsAt().isAfter(shift.startsAt())))
            .sorted(Comparator.<UUID>comparingInt(id -> slot.eligibleScores().get(id)).reversed()
                .thenComparingLong(id -> minutes.getOrDefault(id, 0L))
                .thenComparingInt(id -> consecutiveDays(id, slot.workDate()))
                .thenComparing(UUID::toString))
            .toList();
    }

    private int consecutiveDays(UUID employeeId, LocalDate date) {
        var dates = new HashSet<LocalDate>();
        for (var shift : occupied.getOrDefault(employeeId, List.of())) dates.add(shift.workDate());
        int consecutive = 0;
        for (var day = date.minusDays(1); dates.contains(day); day = day.minusDays(1)) consecutive++;
        for (var day = date.plusDays(1); dates.contains(day); day = day.plusDays(1)) consecutive++;
        return consecutive;
    }
}
