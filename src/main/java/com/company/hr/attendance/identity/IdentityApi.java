package com.company.hr.attendance.identity;

import java.util.*;
import org.springframework.data.domain.Page;

/** Public boundary for modules that need account identity and scheduling authorization. */
public interface IdentityApi {
    record Person(UUID id, String name, String role, boolean locked, UUID managerId) {}
    record ScoreProfile(UUID id, String name, boolean locked, UUID managerId, java.time.Instant createdAt, UUID positionId, java.util.List<String> skills) {}
    java.util.Map<UUID, ScoreProfile> scoreProfiles(java.util.Collection<UUID> ids);
    Person person(UUID id);
    Person lockPerson(UUID id);
    List<UUID> managedEmployeeIds(UUID managerId);
    Page<Person> managedEmployees(UUID managerId, String query, int page, int size);
    Page<Person> employeePage(UUID actorId, String query, int page, int size);
    boolean canReceiveRegistrationReminders(UUID employeeId, UUID sessionId);
    Map<UUID, String> names(Collection<UUID> ids);
}
