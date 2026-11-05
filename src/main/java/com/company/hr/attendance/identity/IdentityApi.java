package com.company.hr.attendance.identity;

import java.util.*;

/** Public boundary for modules that need account identity and scheduling authorization. */
public interface IdentityApi {
    record Person(UUID id, String name, String role, boolean locked, UUID managerId) {}
    Person person(UUID id);
    Person lockPerson(UUID id);
    List<UUID> managedEmployeeIds(UUID managerId);
    Map<UUID, String> names(Collection<UUID> ids);
}
