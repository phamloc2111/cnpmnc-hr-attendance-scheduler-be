package com.company.hr.attendance.workschedule.domain;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record SchedulePublishedEvent(Set<UUID> employeeIds, LocalDate from, LocalDate to) {}
